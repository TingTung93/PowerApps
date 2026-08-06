import sys

file_path = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\QuickPick.pa.yaml'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace with parens block
old_parens = '''                                                      With({
                                                          gtinIdx: Find("(01)", localCompleteBarcode),
                                                          expIdx: Find("(17)", localCompleteBarcode),
                                                          batchIdx: Find("(10)", localCompleteBarcode),
                                                          serialIdx: Find("(21)", localCompleteBarcode)
                                                      },'''

new_parens = '''                                                      With({
                                                          gtinIdx: Find("(01)", localCompleteBarcode),
                                                          expIdx: Find("(17)", localCompleteBarcode),
                                                          batchIdx: Find("(10)", localCompleteBarcode),
                                                          serialIdx: Find("(21)", localCompleteBarcode),
                                                          serial91Idx: Find("(91)", localCompleteBarcode)
                                                      },'''

content = content.replace(old_parens, new_parens)


old_parens_out = '''                                                          serial_out: If(serialIdx > 0, 
                                                              With({
                                                                  rem: Mid(localCompleteBarcode, serialIdx + 4),
                                                                  nParen: Find("(", Mid(localCompleteBarcode, serialIdx + 4))
                                                              },
                                                              If(nParen > 0, Left(rem, nParen - 1), rem)
                                                              ), 
                                                          "")
                                                      }),'''

new_parens_out = '''                                                          serial_out: If(serialIdx > 0, 
                                                              With({
                                                                  rem: Mid(localCompleteBarcode, serialIdx + 4),
                                                                  nParen: Find("(", Mid(localCompleteBarcode, serialIdx + 4))
                                                              },
                                                              If(nParen > 0, Left(rem, nParen - 1), rem)
                                                              ), 
                                                          ""),
                                                          serial91_out: If(serial91Idx > 0, 
                                                              With({
                                                                  rem: Mid(localCompleteBarcode, serial91Idx + 4),
                                                                  nParen: Find("(", Mid(localCompleteBarcode, serial91Idx + 4))
                                                              },
                                                              If(nParen > 0, Left(rem, nParen - 1), rem)
                                                              ), 
                                                          "")
                                                      }),'''

content = content.replace(old_parens_out, new_parens_out)

old_no_parens = '''                                                          serial_out: With({
                                                              sStart: Coalesce(
                                                                  If(Find("21", cleanPrefix) = 1, 3, Blank()),
                                                                  If(Find("21", cleanPrefix) = 17, 19, Blank()),
                                                                  If(Find("21", cleanPrefix) = 25, 27, Blank()),
                                                                  With({idx: Find(Char(29) & "21", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                              )
                                                          }, If(!IsBlank(sStart), 
                                                              With({rem: Mid(cleanPrefix, sStart), gsIdx: Find(Char(29), Mid(cleanPrefix, sStart))}, 
                                                                  If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                          ""))
                                                      }'''

new_no_parens = '''                                                          serial_out: With({
                                                              sStart: Coalesce(
                                                                  If(Find("21", cleanPrefix) = 1, 3, Blank()),
                                                                  If(Find("21", cleanPrefix) = 17, 19, Blank()),
                                                                  If(Find("21", cleanPrefix) = 25, 27, Blank()),
                                                                  With({idx: Find(Char(29) & "21", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                              )
                                                          }, If(!IsBlank(sStart), 
                                                              With({rem: Mid(cleanPrefix, sStart), gsIdx: Find(Char(29), Mid(cleanPrefix, sStart))}, 
                                                                  If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                          "")),
                                                          serial91_out: With({
                                                              sStart: Coalesce(
                                                                  If(Find("91", cleanPrefix) = 1, 3, Blank()),
                                                                  If(Find("91", cleanPrefix) = 17, 19, Blank()),
                                                                  If(Find("91", cleanPrefix) = 25, 27, Blank()),
                                                                  With({idx: Find(Char(29) & "91", cleanPrefix)}, If(idx > 0, idx + 3, Blank()))
                                                              )
                                                          }, If(!IsBlank(sStart), 
                                                              With({rem: Mid(cleanPrefix, sStart), gsIdx: Find(Char(29), Mid(cleanPrefix, sStart))}, 
                                                                  If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), 
                                                          ""))
                                                      }'''

content = content.replace(old_no_parens, new_no_parens)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Success for QuickPick')


file_path_rs = r'E:\PowerApps\MAMC_TS_Sources_v2\Src\RapidScan.pa.yaml'
with open(file_path_rs, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace with parens block
old_parens = '''                                            With({
                                                gtinIdx: Find("(01)", rawScan),
                                                expIdx: Find("(17)", rawScan),
                                                batchIdx: Find("(10)", rawScan),
                                                serialIdx: Find("(21)", rawScan)
                                            },'''

new_parens = '''                                            With({
                                                gtinIdx: Find("(01)", rawScan),
                                                expIdx: Find("(17)", rawScan),
                                                batchIdx: Find("(10)", rawScan),
                                                serialIdx: Find("(21)", rawScan),
                                                serial91Idx: Find("(91)", rawScan)
                                            },'''

content = content.replace(old_parens, new_parens)


old_parens_out = '''                                                serial_out: If(serialIdx > 0, 
                                                    With({
                                                        rem: Mid(rawScan, serialIdx + 4),
                                                        nParen: Find("(", Mid(rawScan, serialIdx + 4))
                                                    }, If(nParen > 0, Left(rem, nParen - 1), rem)), "")
                                            }),'''

new_parens_out = '''                                                serial_out: If(serialIdx > 0, 
                                                    With({
                                                        rem: Mid(rawScan, serialIdx + 4),
                                                        nParen: Find("(", Mid(rawScan, serialIdx + 4))
                                                    }, If(nParen > 0, Left(rem, nParen - 1), rem)), ""),
                                                serial91_out: If(serial91Idx > 0, 
                                                    With({
                                                        rem: Mid(rawScan, serial91Idx + 4),
                                                        nParen: Find("(", Mid(rawScan, serial91Idx + 4))
                                                    }, If(nParen > 0, Left(rem, nParen - 1), rem)), "")
                                            }),'''

content = content.replace(old_parens_out, new_parens_out)

old_no_parens = '''                                                serial_out: With({sIdx: Find("21", cleanPrefix, 15)}, If(sIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, sIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, sIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), ""))
                                            }'''

new_no_parens = '''                                                serial_out: With({sIdx: Find("21", cleanPrefix, 15)}, If(sIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, sIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, sIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), "")),
                                                serial91_out: With({sIdx: Find("91", cleanPrefix, 15)}, If(sIdx > 0, 
                                                    With({
                                                        rem: Mid(cleanPrefix, sIdx + 2), 
                                                        gsIdx: Find(Char(29), Mid(cleanPrefix, sIdx + 2))
                                                    },
                                                    If(gsIdx > 0, Left(rem, gsIdx - 1), rem)), ""))
                                            }'''

content = content.replace(old_no_parens, new_no_parens)

with open(file_path_rs, 'w', encoding='utf-8') as f:
    f.write(content)

print('Success for RapidScan')
