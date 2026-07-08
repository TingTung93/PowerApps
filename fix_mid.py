import sys

files = [
    r'E:\PowerApps\MAMC_TS_Sources_v2\Src\QuickPick.pa.yaml',
    r'E:\PowerApps\MAMC_TS_Sources_v2\Src\RapidScan.pa.yaml'
]

for file_path in files:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace Find("XX", cleanPrefix) = Y with Mid(cleanPrefix, Y, 2) = "XX"
    
    # 17
    content = content.replace('If(Find("17", cleanPrefix) = 1,', 'If(Mid(cleanPrefix, 1, 2) = "17",')
    content = content.replace('If(Find("17", cleanPrefix) = 17,', 'If(Mid(cleanPrefix, 17, 2) = "17",')
    content = content.replace('If(Find("17", cleanPrefix) = 25,', 'If(Mid(cleanPrefix, 25, 2) = "17",')

    # 10
    content = content.replace('If(Find("10", cleanPrefix) = 1,', 'If(Mid(cleanPrefix, 1, 2) = "10",')
    content = content.replace('If(Find("10", cleanPrefix) = 17,', 'If(Mid(cleanPrefix, 17, 2) = "10",')
    content = content.replace('If(Find("10", cleanPrefix) = 25,', 'If(Mid(cleanPrefix, 25, 2) = "10",')

    # 21
    content = content.replace('If(Find("21", cleanPrefix) = 1,', 'If(Mid(cleanPrefix, 1, 2) = "21",')
    content = content.replace('If(Find("21", cleanPrefix) = 17,', 'If(Mid(cleanPrefix, 17, 2) = "21",')
    content = content.replace('If(Find("21", cleanPrefix) = 25,', 'If(Mid(cleanPrefix, 25, 2) = "21",')

    # 91
    content = content.replace('If(Find("91", cleanPrefix) = 1,', 'If(Mid(cleanPrefix, 1, 2) = "91",')
    content = content.replace('If(Find("91", cleanPrefix) = 17,', 'If(Mid(cleanPrefix, 17, 2) = "91",')
    content = content.replace('If(Find("91", cleanPrefix) = 25,', 'If(Mid(cleanPrefix, 25, 2) = "91",')

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f'Successfully updated {file_path}')
