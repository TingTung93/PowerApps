import sys

filepath = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\RapidScan.pa.yaml'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if '                  - Gallery4:' in line:
        start_idx = i
    if '            - FooterContainer1_1:' in line and start_idx != -1:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    new_gallery = '''                  - Gallery4:
                      Control: Gallery@2.15.0
                      Variant: BrowseLayout_Vertical_TwoTextOneImageVariant_pcfCore
                      Properties:
                        BorderColor: =RGBA(243, 242, 241, 1)
                        FocusedBorderColor: =RGBA(98, 100, 167, 1)
                        FocusedBorderThickness: =2
                        Items: =colScannedItems
                        LayoutMaxHeight: =
                        LayoutMaxWidth: =
                        TemplateSize: =70
                        WrapCount: =
                      Children:
                        - Rectangle2:
                            Control: Rectangle@2.3.0
                            Properties:
                              BorderColor: =RGBA(53, 61, 63, 1)
                              BorderThickness: =1
                              DisabledFill: =RGBA(0,0,0,0)
                              Fill: =RGBA(0,0,0,0)
                              FocusedBorderColor: =RGBA(98, 100, 167, 1)
                              Height: =Parent.TemplateHeight
                              HoverFill: =RGBA(0,0,0,0)
                              OnSelect: =Select(Parent)
                              PressedFill: =RGBA(0,0,0,0)
                              TabIndex: =0
                              Width: =Parent.TemplateWidth
                        - HtmlCard:
                            Control: HtmlViewer@1.0.6
                            Properties:
                              HtmlText: |-
                                ="<div style='padding: 12px; font-family: Segoe UI, sans-serif; line-height: 1.5; color: #111827; height: 100%; box-sizing: border-box; overflow: hidden;'>
                                    <div style='font-weight: 600; font-size: 15px; margin-bottom: 4px;'>" & ThisItem.ProductName & "</div>
                                    <div style='font-size: 13px; color: #6B7280;'>
                                        Batch: <span style='font-weight: 500; color: #374151;'>" & ThisItem.Batch & "</span> &nbsp;|&nbsp; 
                                        Exp: <span style='font-weight: 500; color: #374151;'>" & Text(ThisItem.Expiration, "mm/dd/yyyy") & "</span>
                                    </div>
                                </div>"
                              Height: =Parent.TemplateHeight
                              Width: =Parent.TemplateWidth - 110
                              PaddingBottom: =0
                              PaddingLeft: =0
                              PaddingRight: =0
                              PaddingTop: =0
                        - QtyInput:
                            Control: TextInput@2.95.0
                            Properties:
                              Default: =ThisItem.Quantity
                              Format: =TextFormat.Number
                              OnChange: =Patch(colScannedItems, ThisItem, {Quantity: Value(Self.Text)})
                              Width: =80
                              Height: =40
                              X: =Parent.TemplateWidth - 96
                              Y: =((Parent.TemplateHeight - Self.Height) / 2)
                              Align: =Align.Center
                              Size: =16
                              FontWeight: =FontWeight.Semibold
                              RadiusBottomLeft: =6
                              RadiusBottomRight: =6
                              RadiusTopLeft: =6
                              RadiusTopRight: =6
                              BorderThickness: =2
                              BorderColor: =RGBA(209, 213, 219, 1)
                              HoverBorderColor: =gblTheme.Primary
                              FocusedBorderColor: =gblTheme.Primary
'''
    new_lines = lines[:start_idx] + [new_gallery] + lines[end_idx:]
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print("Successfully replaced Gallery4 with input controls")
else:
    print("Gallery4 bounds not found")

