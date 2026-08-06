import sys
import re

def update_gallery_template_size(filepath, gallery_name, new_size):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the block for the gallery properties
    pattern = rf'(\s*-\s*{gallery_name}:\s*\n\s*Control: Gallery[^\n]*\n\s*Variant: [^\n]*\n\s*Properties:\s*\n)'
    match = re.search(pattern, content)
    
    if match:
        insertion_point = match.end()
        # Find the indentation of the Properties: block children
        indent_match = re.search(r'(\s+)[a-zA-Z0-9_]+:', content[insertion_point:insertion_point+100])
        indent = indent_match.group(1) if indent_match else "                                "
        
        # Check if TemplateSize already exists
        if f'{indent}TemplateSize:' not in content[insertion_point:insertion_point+500]:
            new_content = content[:insertion_point] + f'{indent}TemplateSize: ={new_size}\n' + content[insertion_point:]
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f'Successfully added TemplateSize to {gallery_name} in {filepath}')
        else:
            # Replace existing TemplateSize
            new_content = re.sub(rf'{indent}TemplateSize: =.*?\n', f'{indent}TemplateSize: ={new_size}\n', content)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f'Successfully updated TemplateSize for {gallery_name} in {filepath}')
    else:
        print(f'Could not find {gallery_name} in {filepath}')

update_gallery_template_size(r'E:\PowerApps\MAMC_TS_Sources_v2\Src\Inventory.pa.yaml', 'Gallery2', '160')
update_gallery_template_size(r'E:\PowerApps\MAMC_TS_Sources_v2\Src\ItemList.pa.yaml', 'Gallery1', '140')
