import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\QuickPick.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# ExistenceCheck_1
content = content.replace(
    'Fill: =If(!IsBlank(existingInventory), Color.LightGreen, Color.Aqua)',
    'Fill: =If(IsBlank(BarcodeInput_1.Value) || BarcodeInput_1.Value = "", Transparent, If(!IsBlank(existingInventory), gblTheme.Success, gblTheme.Warning))\n                                      Color: =gblTheme.Surface'
)
old_text = '''                                    Text: |-
                                      =If(BarcodeInput_1.Value = Blank(), "",
                                         If(!IsBlank(existingInventory), 
                                            "Barcode already exists in the inventory. Editing the entry.",
                                            "Barcode is new. You can add it to the inventory.")
                                      )'''

new_text = '''                                    Text: |-
                                      =If(BarcodeInput_1.Value = Blank(), "",
                                         If(!IsBlank(existingInventory), 
                                            "✓ Item found in inventory. Editing entry.",
                                            "⚠ New barcode detected. Ready to add.")
                                      )'''
content = content.replace(old_text, new_text)

# BarcodeInput_1
content = content.replace(
    'Fill: =RGBA(152, 208, 70, 1)',
    'Fill: =gblTheme.Surface\n                                      BorderColor: =gblTheme.Primary\n                                      BorderThickness: =2'
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print('Updated QuickPick specialized UI')
