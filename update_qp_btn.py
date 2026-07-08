import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\QuickPick.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

old_text = '''                              OnSelect: =SubmitForm(InventoryForm_1)
                              Text: ="Submit"
                              Width: =120'''
new_text = '''                              DisplayMode: =If(InventoryForm_1.Valid, DisplayMode.Edit, DisplayMode.Disabled)
                              OnSelect: =SubmitForm(InventoryForm_1)
                              Text: ="Submit"
                              Width: =120'''

if 'DisplayMode: =If(InventoryForm_1.Valid' not in content:
    content = content.replace(old_text, new_text)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Updated QuickPick Submit button logic')
else:
    print('Already updated')
