"""Pack step: make NLLB's tied LM head cheap in the merged decoder.

The Xenova int8 export computes logits inside both If branches as
    Transpose(W_int8) -> DequantizeLinear (1 GB of fp32!) -> MatMul
on every decoding step. That costs ~1.3 GB of transient RAM and most of the
step time. This rewrite:
  * stores the transposed int8 weight once as a top-level initializer,
  * makes both branches output the final hidden state instead of logits,
  * computes logits once, after the If, with DynamicQuantizeLinear + MatMulInteger
    (a top-level constant, so ONNX Runtime prepacks it at load).

    ~/zbor-build/.venv/bin/python -I tools/fix_lm_head.py IN.onnx OUT.onnx
"""
import sys

import numpy as np
import onnx
from onnx import helper, numpy_helper, TensorProto

W = "model.shared.weight_merged_0_quantized"
W_SCALE = "model.shared.weight_merged_0_scale"
W_ZP = "model.shared.weight_merged_0_zero_point"
W_T = "lm_head.weight_t_quantized"
HIDDEN = "/model/decoder/layer_norm/Add_1_output_0"
LM_HIDDEN = "lm_hidden"

model = onnx.load(sys.argv[1])
g = model.graph
weight = next(i for i in g.initializer if i.name == W)
g.initializer.append(numpy_helper.from_array(np.ascontiguousarray(numpy_helper.to_array(weight).T), W_T))

DROP = {"Transpose", "DequantizeLinear", "MatMul"}
LM_OUTPUTS = {"model.shared.weight_transposed_quantized", "model.shared.weight_transposed", "logits"}

if_node = next(n for n in g.node if n.op_type == "If")
for attr in if_node.attribute:
    branch = attr.g
    keep = [n for n in branch.node if not (n.op_type in DROP and set(n.output) & LM_OUTPUTS)]
    assert len(branch.node) - len(keep) == 3, f"{attr.name}: expected 3 LM-head nodes"
    del branch.node[:]
    branch.node.extend(keep)
    out = branch.output[0]
    assert out.name == "logits"
    out.name = HIDDEN
    out.type.tensor_type.shape.dim[2].dim_value = 1024
    out.type.tensor_type.shape.dim[2].ClearField("dim_param")
assert if_node.output[0] == "logits"
if_node.output[0] = LM_HIDDEN

g.node.extend([
    helper.make_node("DynamicQuantizeLinear", [LM_HIDDEN], ["lm_q", "lm_q_scale", "lm_q_zp"], name="lm_head/quant"),
    helper.make_node("MatMulInteger", ["lm_q", W_T, "lm_q_zp", W_ZP], ["lm_i32"], name="lm_head/matmul"),
    helper.make_node("Cast", ["lm_i32"], ["lm_f32"], to=TensorProto.FLOAT, name="lm_head/cast"),
    helper.make_node("Mul", ["lm_q_scale", W_SCALE], ["lm_scale"], name="lm_head/scale"),
    helper.make_node("Mul", ["lm_f32", "lm_scale"], ["logits"], name="lm_head/logits"),
])
onnx.checker.check_model(model, full_check=False)
onnx.save(model, sys.argv[2])
print(f"LM head hoisted -> {sys.argv[2]}")
