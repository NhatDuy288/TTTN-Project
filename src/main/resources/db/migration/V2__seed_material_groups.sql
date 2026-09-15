-- Five accepted MVP material groups from Project Master section 11G and BRD section 2.2.1.
INSERT INTO "material_group" ("code", "name", "requires_accounting", "active") VALUES
  ('CARD-PHOI', 'Phôi thẻ', true, true),
  ('POS-THIETBI', 'POS', false, true),
  ('CARD-VL-KHAC', 'Vật tư khác của thẻ', false, true),
  ('POS-VL-KHAC', 'Vật tư khác của POS', false, true),
  ('ATM-VL-KHAC', 'Vật tư khác của ATM', false, true);
