import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\RapidScan.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

old_text = '''                          Text: ="Submit All Scans"
                          Width: =150'''
new_text = '''                          DisplayMode: =If(varIsSubmitting || IsEmpty(colScannedItems), DisplayMode.Disabled, DisplayMode.Edit)
                          Text: ="Submit All Scans"
                          Width: =150'''

if 'DisplayMode: =If(varIsSubmitting' not in content:
    content = content.replace(old_text, new_text)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Updated RapidScan Submit button logic')
else:
    print('Already updated')
