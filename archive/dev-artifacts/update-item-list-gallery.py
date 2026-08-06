import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\ItemList.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if '- Title3:' in line:
        start_idx = i
    if '- RightContainer1_4:' in line and start_idx != -1:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    new_html_card = '''                          - HtmlCard:
                              Control: HtmlViewer@1.0.6
                              Properties:
                                HtmlText: |-
                                  ="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827;'>
                                      <div style='display: flex; justify-content: space-between; align-items: flex-start;'>
                                          <div style='font-weight: 600; font-size: 16px; margin-bottom: 4px;'>" & ThisItem.Name & "</div>
                                          <div style='font-size: 13px; font-weight: bold; color: " & If(ThisItem.Expiration < Today(), "#DC2626", If(ThisItem.Expiration <= DateAdd(Today(), 7), "#C2410C", "#374151")) & ";'>" & 
                                              If(ThisItem.Expiration < Today(), "EXPIRED", If(ThisItem.Expiration <= DateAdd(Today(), 30), DateDiff(Today(), ThisItem.Expiration) & "d", "")) & "
                                          </div>
                                      </div>
                                      <div style='font-size: 12px; margin-bottom: 8px; color: " & If(ThisItem.Expiration < Today(), "#DC2626", If(ThisItem.Expiration <= DateAdd(Today(), 7), "#C2410C", "#6B7280")) & ";'>
                                          Expires: " & Text(DateValue(ThisItem.Expiration, "en"), "mm/dd/yyyy") & "
                                      </div>
                                      <div style='font-size: 12px; color: #6B7280;'>
                                          Batch: " & ThisItem.Batch & If(!IsBlank(ThisItem.Quantity), " • Qty: " & ThisItem.Quantity & " " & LookUp(GTIN_Lookups, ThisItem.ItemID = GTIN, 'Unit Quantities'), "") & "
                                      </div>
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
    print("Successfully replaced Gallery items with HtmlCard in ItemList")
else:
    print(f"Error: start={start_idx}, end={end_idx}")
