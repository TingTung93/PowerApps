import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\Inventory.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find Title4: and Rectangle3:
start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if '- Title4:' in line:
        start_idx = i
    if '- Rectangle3:' in line and start_idx != -1:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    new_html_card = '''                              - HtmlCard:
                                  Control: HtmlViewer@1.0.6
                                  Properties:
                                    HtmlText: |-
                                      ="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827;'>
                                          <div style='font-weight: 600; font-size: 16px; margin-bottom: 4px;'>" & ThisItem.Name & "</div>
                                          <div style='font-size: 12px; color: #6B7280; margin-bottom: 8px;'>Man: " & ThisItem.Manufacturer_Lookup & "</div>
                                          <div style='display: flex; flex-wrap: wrap; gap: 8px; font-size: 13px;'>
                                              <span style='background: " & If(ThisItem.Quantity < 1, "#FEE2E2; color: #DC2626;", "#F3F4F6; color: #374151;") & " padding: 2px 8px; border-radius: 9999px;'>
                                                  Qty: " & ThisItem.Quantity & " " & LookUp(GTIN_Lookups, ThisItem.ItemID = GTIN, 'Unit Quantities') & "
                                              </span>
                                              <span style='background: " & If(ThisItem.Expiration < Today(), "#FEE2E2; color: #DC2626;", If(ThisItem.Expiration <= DateAdd(Today(), 7), "#FFEDD5; color: #C2410C", "#F3F4F6; color: #374151;")) & " padding: 2px 8px; border-radius: 9999px;'>
                                                  Exp: " & DateValue(ThisItem.Expiration, "en") & "
                                              </span>
                                              <span style='background: #F3F4F6; color: #374151; padding: 2px 8px; border-radius: 9999px;'>
                                                  Lot: " & ThisItem.Batch & "
                                              </span>
                                              <span style='background: #E0E7FF; color: #4338CA; padding: 2px 8px; border-radius: 9999px;'>
                                                  " & ThisItem.'Item Category' & "
                                              </span>
                                          </div>
                                          <div style='font-size: 11px; color: #9CA3AF; margin-top: 8px;'>Last Modified: " & DateValue(ThisItem.'Modified On', "en") & "</div>
                                      </div>"
                                    Height: =Parent.TemplateHeight
                                    Width: =Parent.TemplateWidth
                                    OnSelect: =Select(Parent)
                                    PaddingBottom: =0
                                    PaddingLeft: =0
                                    PaddingRight: =0
                                    PaddingTop: =0
'''
    new_lines = lines[:start_idx] + [new_html_card] + lines[end_idx:]
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print("Successfully replaced Gallery items with HtmlCard in Inventory")
else:
    print(f"Error: start={start_idx}, end={end_idx}")
