import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\Inventory.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '''="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827;'>''',
    '''="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827; height: 100%; box-sizing: border-box; overflow: hidden;'>'''
)
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\ItemList.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '''="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827;'>''',
    '''="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827; height: 100%; box-sizing: border-box; overflow: hidden;'>'''
)
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print('Added bounds to HTML')
