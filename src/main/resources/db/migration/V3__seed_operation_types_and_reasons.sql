-- Accepted request configuration from Project Master sections 11F and 11L.
INSERT INTO "operation_type"
  ("code", "name", "direction", "requires_po", "requires_accounting", "default_condition", "active", "display_order")
VALUES
  ('IMP_CARD', 'Nhập kho phôi thẻ mới', 'IMPORT', true, true, 'NEW', true, 10),
  ('IMP_NEW', 'Nhập kho vật tư mới', 'IMPORT', true, false, 'NEW', true, 20),
  ('IMP_OTHER', 'Nhập kho cũ, hỏng, tạm', 'IMPORT', false, false, NULL, true, 30),
  ('EXP_CARD', 'Xuất kho phôi thẻ mới', 'EXPORT', false, true, 'NEW', true, 10),
  ('EXP_NEW', 'Xuất kho vật tư mới', 'EXPORT', false, false, 'NEW', true, 20),
  ('EXP_OTHER', 'Xuất kho cũ, hỏng, tạm', 'EXPORT', false, false, NULL, true, 30),
  ('WWT', 'Điều chuyển kho', 'TRANSFER', false, false, NULL, true, 10);

INSERT INTO "operation_type_material_group" ("operation_type_id", "material_group_id")
SELECT operation_type.id, material_group.id
FROM (VALUES
  ('IMP_CARD', 'CARD-PHOI'),
  ('IMP_NEW', 'POS-THIETBI'),
  ('IMP_NEW', 'CARD-VL-KHAC'),
  ('IMP_NEW', 'POS-VL-KHAC'),
  ('IMP_NEW', 'ATM-VL-KHAC'),
  ('IMP_OTHER', 'CARD-PHOI'),
  ('IMP_OTHER', 'POS-THIETBI'),
  ('IMP_OTHER', 'CARD-VL-KHAC'),
  ('IMP_OTHER', 'POS-VL-KHAC'),
  ('IMP_OTHER', 'ATM-VL-KHAC'),
  ('EXP_CARD', 'CARD-PHOI'),
  ('EXP_NEW', 'POS-THIETBI'),
  ('EXP_NEW', 'CARD-VL-KHAC'),
  ('EXP_NEW', 'POS-VL-KHAC'),
  ('EXP_NEW', 'ATM-VL-KHAC'),
  ('EXP_OTHER', 'CARD-PHOI'),
  ('EXP_OTHER', 'POS-THIETBI'),
  ('EXP_OTHER', 'CARD-VL-KHAC'),
  ('EXP_OTHER', 'POS-VL-KHAC'),
  ('EXP_OTHER', 'ATM-VL-KHAC'),
  ('WWT', 'CARD-PHOI'),
  ('WWT', 'POS-THIETBI'),
  ('WWT', 'CARD-VL-KHAC'),
  ('WWT', 'POS-VL-KHAC'),
  ('WWT', 'ATM-VL-KHAC')
) AS mapping(operation_code, material_group_code)
JOIN "operation_type" ON operation_type.code = mapping.operation_code
JOIN "material_group" ON material_group.code = mapping.material_group_code;

INSERT INTO "operation_type_condition" ("operation_type_id", "condition")
SELECT operation_type.id, mapping.condition
FROM (VALUES
  ('IMP_CARD', 'NEW'),
  ('IMP_NEW', 'NEW'),
  ('IMP_OTHER', 'OLD'),
  ('IMP_OTHER', 'BROKEN'),
  ('IMP_OTHER', 'TEMP'),
  ('EXP_CARD', 'NEW'),
  ('EXP_NEW', 'NEW'),
  ('EXP_OTHER', 'OLD'),
  ('EXP_OTHER', 'BROKEN'),
  ('EXP_OTHER', 'TEMP'),
  ('WWT', 'NEW'),
  ('WWT', 'OLD'),
  ('WWT', 'BROKEN'),
  ('WWT', 'TEMP')
) AS mapping(operation_code, condition)
JOIN "operation_type" ON operation_type.code = mapping.operation_code;

INSERT INTO "reason"
  ("code", "name", "direction", "requires_note", "active", "display_order")
VALUES
  ('IMPORT_ADJUSTMENT_SURPLUS', 'Nhập điều chỉnh thừa', 'IMPORT', false, true, 10),
  ('IMPORT_RETURN', 'Nhập hàng thu hồi', 'IMPORT', false, true, 20),
  ('IMPORT_OTHER', 'Nhập khác', 'IMPORT', true, true, 30),
  ('EXPORT_USE', 'Xuất sử dụng', 'EXPORT', false, true, 10),
  ('EXPORT_ADJUSTMENT_SHORTAGE', 'Xuất điều chỉnh thiếu', 'EXPORT', false, true, 20),
  ('EXPORT_OTHER', 'Xuất khác', 'EXPORT', true, true, 30),
  ('TRANSFER_INTERNAL', 'Chuyển theo bộ phận sử dụng', 'TRANSFER', false, true, 10),
  ('TRANSFER_WAREHOUSE_CLOSURE', 'Chuyển do kho ngừng hoạt động', 'TRANSFER', false, true, 20),
  ('TRANSFER_OTHER', 'Khác', 'TRANSFER', true, true, 30);
