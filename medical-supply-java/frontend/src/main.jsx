import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert, AppBar, Box, Button, Card, CardContent, Chip, CssBaseline, Divider, Drawer,
  FormControlLabel, List, ListItemButton, ListItemIcon, ListItemText, Snackbar, Stack,
  Switch, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, ThemeProvider, Toolbar, Typography
} from '@mui/material'
import DashboardRounded from '@mui/icons-material/DashboardRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import AppRegistrationRounded from '@mui/icons-material/AppRegistrationRounded'
import QrCode2Rounded from '@mui/icons-material/QrCode2Rounded'
import TroubleshootRounded from '@mui/icons-material/TroubleshootRounded'
import AssessmentRounded from '@mui/icons-material/AssessmentRounded'
import FactCheckRounded from '@mui/icons-material/FactCheckRounded'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import FolderRounded from '@mui/icons-material/FolderRounded'
import DownloadRounded from '@mui/icons-material/DownloadRounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import QRCode from 'qrcode'
import { api } from './api'
import { theme } from './theme'

const DRAWER = 240
const NAV = [
  ['dashboard', 'Dashboard', <DashboardRounded />],
  ['scan', 'Receive stock', <QrCodeScannerRounded />],
  ['inventory', 'Inventory', <Inventory2Rounded />],
  ['count', 'Inventory count', <FactCheckRounded />],
  ['management', 'Management', <AssessmentRounded />],
  ['registration', 'Registration', <AppRegistrationRounded />],
  ['labels', 'Paper count ledger', <QrCode2Rounded />],
  ['settings', 'Settings', <SettingsRounded />],
  ['diagnostics', 'Diagnostics', <TroubleshootRounded />]
]

function expiryColor(iso) {
  if (!iso) return 'default'
  const days = (new Date(iso) - new Date()) / 86400000
  if (days < 0) return 'error'
  if (days <= 7) return 'error'
  if (days <= 30) return 'warning'
  return 'default'
}

function App() {
  const [view, setView] = useState('dashboard')
  const [state, setState] = useState(null)
  const [toast, setToast] = useState('')

  const refresh = useCallback(async () => {
    try { setState(await api.state()) } catch (e) { setToast(e.message) }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15000)
    return () => clearInterval(id)
  }, [refresh])

  const run = useCallback(async (fn, ok) => {
    try { await fn(); if (ok) setToast(ok); await refresh() }
    catch (e) { setToast(e.message) }
  }, [refresh])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <style>{`@media print {
        .app-chrome { display: none !important; }
        .ledger-screen-only { display: none !important; }
        .ledger-print { display: block !important; }
        body { background: white !important; }
        @page { size: landscape; margin: 0.4in; }
      }`}</style>
      <AppBar className="app-chrome" position="fixed" elevation={0}
        sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 2 }}>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6" fontWeight={700}>Medical Supply Tracking</Typography>
            <Typography variant="caption" sx={{ opacity: .82 }}>
              Inventory operations and supply readiness
            </Typography>
          </Box>
          {state?.configured && <Chip label="Shared store connected" color="success" size="small" />}
        </Toolbar>
      </AppBar>
      <Drawer className="app-chrome" variant="permanent" sx={{ width: DRAWER, '& .MuiDrawer-paper': {
        width: DRAWER, borderRightColor: 'divider', backgroundImage: 'none'
      } }}>
        <Toolbar />
        <Typography variant="overline" color="text.secondary" sx={{ px: 2.5, pt: 2 }}>Workspaces</Typography>
        <List sx={{ px: 1 }}>
          {NAV.map(([key, label, icon]) => (
            <ListItemButton key={key} selected={view === key} onClick={() => setView(key)}
              sx={{ borderRadius: 1.5, mb: .5 }}>
              <ListItemIcon>{icon}</ListItemIcon>
              <ListItemText primary={label} />
            </ListItemButton>
          ))}
        </List>
        <Box sx={{ mt: 'auto', p: 2 }}>
          <Divider sx={{ mb: 2 }} />
          <Typography variant="caption" color="text.secondary" display="block">Current folder</Typography>
          <Typography variant="caption" title={state?.sharedRoot || ''} noWrap display="block">
            {state?.sharedRoot || 'Not configured'}
          </Typography>
        </Box>
      </Drawer>
      <Box component="main" sx={{ ml: `${DRAWER}px`, p: { xs: 2, md: 3 }, mt: 8,
        minHeight: '100vh', '@media print': { ml: 0, p: 0, mt: 0 } }}>
        <Box sx={{ maxWidth: 1440, mx: 'auto' }}>
        {!state ? <Typography>Loading…</Typography>
          : !state.configured && !['diagnostics', 'settings'].includes(view)
          ? <FolderPrompt run={run} />
          : {
              dashboard: <Dashboard state={state} run={run} setView={setView} />,
              scan: <Scan run={run} refresh={refresh} setToast={setToast} />,
              inventory: <Inventory state={state} run={run} />,
              count: <Count state={state} run={run} />,
              management: <Management state={state} run={run} />,
              registration: <Registration state={state} run={run} />,
              labels: <Labels state={state} />,
              settings: <Settings state={state} run={run} />,
              diagnostics: <Diagnostics state={state} run={run} />
            }[view]}
        </Box>
      </Box>
      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast('')} message={toast} />
    </ThemeProvider>
  )
}

function FolderPrompt({ run }) {
  const chooseFolder = () => run(async () => {
    const result = await api.chooseFolder()
    if (result.cancelled) throw new Error('Folder selection cancelled')
  }, 'Folder set')
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Synchronized folder</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Choose the local OneDrive-synchronized folder used by your team.
      </Typography>
      <Button variant="contained" onClick={chooseFolder}>Choose folder…</Button>
    </CardContent></Card>
  )
}

function PageHeader({ title, description, actions }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between"
      alignItems={{ sm: 'center' }} spacing={2} sx={{ mb: 3 }}>
      <Box>
        <Typography variant="h5">{title}</Typography>
        <Typography variant="body2" color="text.secondary">{description}</Typography>
      </Box>
      {actions && <Stack direction="row" spacing={1}>{actions}</Stack>}
    </Stack>
  )
}

function EmptyState({ children }) {
  return <Box sx={{ py: 5, textAlign: 'center', color: 'text.secondary' }}>{children}</Box>
}

function Dashboard({ state, run, setView }) {
  const d = state.dashboard || {}
  const tiles = [
    ['Active SKUs', d.distinctSkus, 'primary.main'],
    ['On-hand value', `$${(d.onHandValue || 0).toFixed(2)}`, 'secondary.main'],
    ['Total units', d.totalUnits, 'text.primary'],
    ['Expired', d.expired, 'error.main'],
    ['Expiring in 30 days', d.expiring30, 'warning.main'],
    ['Out of stock', d.outOfStock, 'error.main']
  ]
  const reorder = (state.reorder || []).filter(r => r.needsReorder)
  return (
    <Stack spacing={2}>
      <PageHeader title="Operations dashboard"
        description="A live view of inventory health, readiness, and action items."
        actions={<Button startIcon={<DownloadRounded />} variant="outlined"
          onClick={() => run(() => api.report(), 'Management report exported')}>Export report</Button>} />
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
        {tiles.map(([label, value, color]) => (
          <Card key={label} sx={{ minWidth: 175, flex: '1 1 175px' }}><CardContent>
            <Typography variant="h4" color={color}>{value ?? 0}</Typography>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
          </CardContent></Card>
        ))}
      </Box>
      {(d.expired > 0 || d.expiring7 > 0) &&
        <Alert severity="warning">Review {d.expired || 0} expired and {d.expiring7 || 0} near-expiry stock lines.</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, gap: 2 }}>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ px: 2.5, py: 2 }}>
          <Typography variant="h6">Priority replenishment</Typography>
          <Typography variant="body2" color="text.secondary">Items currently below PAR or calculated reorder point.</Typography>
        </Box>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>On hand</TableCell>
            <TableCell>Target</TableCell><TableCell>Order</TableCell><TableCell>Est. cost</TableCell></TableRow></TableHead>
          <TableBody>
            {reorder.map(r => (
              <TableRow key={r.gtin}><TableCell>{r.name || r.gtin}</TableCell><TableCell>{r.onHand}</TableCell>
                <TableCell>{r.parProvided ? r.par : r.suggestedPar}</TableCell><TableCell>{r.suggestedOrderQty}</TableCell>
                <TableCell>{(r.estimatedCost || 0).toFixed(2)}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
        {!reorder.length && <EmptyState>No replenishment actions right now.</EmptyState>}
      </CardContent></Card>
      <Card><CardContent>
        <Typography variant="h6" gutterBottom>Quick actions</Typography>
        <Stack spacing={1}>
          <Button variant="contained" onClick={() => setView('scan')}>Receive stock</Button>
          <Button variant="outlined" onClick={() => setView('count')}>Start inventory count</Button>
          <Button variant="outlined" onClick={() => setView('registration')}>Register product</Button>
          <Button variant="text" onClick={() => setView('management')}>Open management workspace</Button>
        </Stack>
        <Divider sx={{ my: 2 }} />
        <Typography variant="caption" color="text.secondary">Activity in last 7 days</Typography>
        <Typography variant="h5">{d.activeEventsLast7 || 0}</Typography>
      </CardContent></Card>
      </Box>
    </Stack>
  )
}

function Scan({ run, refresh, setToast }) {
  const [raw, setRaw] = useState('')
  const [qty, setQty] = useState('1')
  const submit = async () => {
    try {
      const result = await api.receive({ raw, quantity: qty, force: 'false' })
      if (result.needsRegistration) {
        const s = result.suggestion || {}
        const name = window.prompt(`Unknown product ${result.gtin}. Product name:`, s.name || '')
        if (!name) return
        await api.register({ gtin: result.gtin, name, manufacturer: s.manufacturer || '',
          category: s.category || '', source: s.found ? 'GUDID' : 'MANUAL' })
        await api.receive({ raw, quantity: qty, force: 'true' })
      }
      setRaw(''); setToast('Received'); refresh()
    } catch (e) { setToast(e.message) }
  }
  return (
    <Stack spacing={2}>
    <PageHeader title="Receive stock"
      description="Scan a GS1 barcode to capture GTIN, lot, and expiration automatically." />
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>New receipt</Typography>
      <Stack direction="row" spacing={1}>
        <TextField autoFocus fullWidth size="small" label="Barcode" value={raw}
          onChange={e => setRaw(e.target.value)} onKeyDown={e => e.key === 'Enter' && submit()} />
        <TextField size="small" label="Qty" type="number" value={qty} sx={{ width: 100 }}
          onChange={e => setQty(e.target.value)} />
        <Button variant="contained" onClick={submit}>Receive</Button>
      </Stack>
    </CardContent></Card>
    <Alert severity="info">Keep the barcode field focused for rapid scanner entry. Unknown products will open registration.</Alert>
    </Stack>
  )
}

function Inventory({ state, run }) {
  const [filter, setFilter] = useState('')
  const rows = (state.stock || []).filter(l => l.active &&
    (l.name + l.gtin + l.lot).toLowerCase().includes(filter.toLowerCase()))
  return (
    <Stack spacing={2}>
    <PageHeader title="Inventory" description="Search active stock by product, GTIN, or lot and perform daily transactions." />
    <Card><CardContent>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <TextField size="small" label="Search inventory" value={filter}
          onChange={e => setFilter(e.target.value)} sx={{ width: 340 }} />
        <Chip label={`${rows.length} active stock lines`} />
      </Stack>
      <Table size="small">
        <TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
          <TableCell>Qty</TableCell><TableCell>Actions</TableCell></TableRow></TableHead>
        <TableBody>
          {rows.map(l => (
            <TableRow key={l.itemKey}>
              <TableCell><Typography variant="body2" fontWeight={600}>{l.name || 'Unregistered product'}</Typography>
                <Typography variant="caption" color="text.secondary">{l.manufacturer || l.category || ''}</Typography></TableCell>
              <TableCell>{l.gtin}</TableCell><TableCell>{l.lot || '—'}</TableCell>
              <TableCell><Chip size="small" color={expiryColor(l.expirationIso)} label={l.expirationIso || '—'} /></TableCell>
              <TableCell>{l.quantity}</TableCell>
              <TableCell>
                <Button size="small" onClick={() => { const n = window.prompt('Pick quantity', '1'); if (n) run(() => api.pick({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Picked') }}>Pick</Button>
                <Button size="small" onClick={() => { const n = window.prompt('Set quantity', String(l.quantity)); if (n !== null) run(() => api.adjust({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, quantity: n }), 'Adjusted') }}>Adjust</Button>
                <Button size="small" color="error" onClick={() => { const r = window.prompt('Archive reason'); if (r) run(() => api.archive({ gtin: l.gtin, lot: l.lot, expirationIso: l.expirationIso, reason: r }), 'Archived') }}>Archive</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {!rows.length && <EmptyState>No active inventory matches this search.</EmptyState>}
    </CardContent></Card>
    </Stack>
  )
}

function Count({ state, run }) {
  const active = (state.stock || []).filter(line => line.active)
  const [filter, setFilter] = useState('')
  const [scanValue, setScanValue] = useState('')
  const [selectedKey, setSelectedKey] = useState('')
  const [delta, setDelta] = useState('')
  const [scanMessage, setScanMessage] = useState('')
  const [counts, setCounts] = useState(() => Object.fromEntries(
    active.map(line => [line.itemKey, String(line.quantity)])))

  useEffect(() => {
    setCounts(current => Object.fromEntries(active.map(line => [
      line.itemKey, current[line.itemKey] ?? String(line.quantity)
    ])))
  }, [state.stock])

  const rows = active.filter(line =>
    `${line.name} ${line.gtin} ${line.lot}`.toLowerCase().includes(filter.toLowerCase()))
  const changed = active.filter(line =>
    Number(counts[line.itemKey]) !== line.quantity && counts[line.itemKey] !== '')
  const selected = active.find(line => line.itemKey === selectedKey)

  const resolveLedgerQr = () => {
    const scanned = scanValue.trim()
    const key = scanned.startsWith('MSITEM:') ? scanned.slice(7) : scanned
    const match = active.find(line => line.itemKey === key || line.gtin === key)
    if (!match) {
      setSelectedKey('')
      setScanMessage('No active stock line matches that QR code.')
      return
    }
    setSelectedKey(match.itemKey)
    setDelta('')
    setScanMessage('')
  }

  const applyDelta = () => {
    if (!selected) return
    const amount = Number(delta)
    if (!Number.isInteger(amount) || amount === 0) {
      setScanMessage('Enter a whole-number delta such as -2 or +3.')
      return
    }
    const newQuantity = selected.quantity + amount
    if (newQuantity < 0) {
      setScanMessage('That delta would make inventory negative.')
      return
    }
    run(async () => {
      await api.adjust({
        gtin: selected.gtin,
        lot: selected.lot,
        expirationIso: selected.expirationIso,
        quantity: String(newQuantity)
      })
      setScanValue('')
      setSelectedKey('')
      setDelta('')
      setScanMessage('')
    }, `Applied ${amount > 0 ? '+' : ''}${amount} to ${selected.name || selected.gtin}`)
  }

  const save = () => run(async () => {
    for (const line of changed) {
      await api.adjust({
        gtin: line.gtin,
        lot: line.lot,
        expirationIso: line.expirationIso,
        quantity: counts[line.itemKey]
      })
    }
  }, `${changed.length} count${changed.length === 1 ? '' : 's'} posted`)

  return (
    <Stack spacing={2}>
      <PageHeader title="Inventory count"
        description="Reconcile the paper ledger by scanning its QR codes, or enter physical counts in bulk."
        actions={<Button variant="contained" disabled={!changed.length} onClick={save}>
          Post {changed.length || ''} adjustment{changed.length === 1 ? '' : 's'}
        </Button>} />
      <Card><CardContent>
        <Typography variant="h6">Scan paper ledger QR</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Scan a row from the printed ledger, then enter the signed change recorded on paper.
        </Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ md: 'center' }}>
          <TextField autoFocus size="small" label="Scan QR code" value={scanValue}
            onChange={event => setScanValue(event.target.value)}
            onKeyDown={event => event.key === 'Enter' && resolveLedgerQr()} sx={{ minWidth: 340 }} />
          <Button variant="outlined" onClick={resolveLedgerQr}>Look up</Button>
          {selected && <>
            <Divider orientation="vertical" flexItem />
            <Box sx={{ minWidth: 220 }}><Typography variant="body2" fontWeight={700}>
              {selected.name || selected.gtin}</Typography>
              <Typography variant="caption">Lot {selected.lot || '—'} · Current {selected.quantity}</Typography></Box>
            <TextField size="small" label="Delta (+ / -)" type="number" value={delta}
              onChange={event => setDelta(event.target.value)}
              onKeyDown={event => event.key === 'Enter' && applyDelta()} sx={{ width: 150 }} />
            <Button variant="contained" onClick={applyDelta}>Apply delta</Button>
          </>}
        </Stack>
        {scanMessage && <Alert severity="warning" sx={{ mt: 2 }}>{scanMessage}</Alert>}
      </CardContent></Card>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ px: 2, pt: 2 }}><Typography variant="h6">Bulk physical count</Typography>
          <Typography variant="body2" color="text.secondary">Enter counted quantities directly when transcribing the full sheet.</Typography></Box>
        <Box sx={{ p: 2, display: 'flex', gap: 2, alignItems: 'center' }}>
          <TextField size="small" label="Find product or lot" value={filter}
            onChange={event => setFilter(event.target.value)} sx={{ width: 340 }} />
          <Chip label={`${active.length} stock lines`} />
          <Chip color={changed.length ? 'warning' : 'default'} label={`${changed.length} changed`} />
        </Box>
        <Table size="small">
          <TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell>
            <TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
            <TableCell align="right">System qty</TableCell><TableCell sx={{ width: 150 }}>Counted qty</TableCell>
            <TableCell align="right">Variance</TableCell></TableRow></TableHead>
          <TableBody>{rows.map(line => {
            const counted = counts[line.itemKey]
            const variance = counted === '' ? 0 : Number(counted) - line.quantity
            return <TableRow key={line.itemKey} sx={variance ? { backgroundColor: '#fff8e1' } : undefined}>
              <TableCell><Typography variant="body2" fontWeight={600}>{line.name || 'Unregistered product'}</Typography></TableCell>
              <TableCell>{line.gtin}</TableCell><TableCell>{line.lot || '—'}</TableCell>
              <TableCell>{line.expirationIso || '—'}</TableCell><TableCell align="right">{line.quantity}</TableCell>
              <TableCell><TextField size="small" type="number" value={counted ?? ''}
                inputProps={{ min: 0 }} onChange={event => setCounts(value => ({
                  ...value, [line.itemKey]: event.target.value
                }))} /></TableCell>
              <TableCell align="right"><Chip size="small" color={variance ? 'warning' : 'default'}
                label={variance > 0 ? `+${variance}` : variance} /></TableCell>
            </TableRow>
          })}</TableBody>
        </Table>
        {!rows.length && <EmptyState>No active stock lines match this count.</EmptyState>}
      </CardContent></Card>
    </Stack>
  )
}

function Management({ state, run }) {
  const reorder = (state.reorder || []).filter(item => item.needsReorder)
  const catalog = state.catalog || []
  const estimated = reorder.reduce((sum, item) => sum + (item.estimatedCost || 0), 0)
  return (
    <Stack spacing={2}>
      <PageHeader title="Management"
        description="Plan replenishment, review catalog controls, and export decision-ready reports."
        actions={<Button variant="contained" startIcon={<DownloadRounded />}
          onClick={() => run(() => api.report(), 'HTML, CSV, and PDF reports exported')}>
          Export report package
        </Button>} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Reorder lines</Typography>
          <Typography variant="h4">{reorder.length}</Typography></CardContent></Card>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Estimated order cost</Typography>
          <Typography variant="h4">${estimated.toFixed(2)}</Typography></CardContent></Card>
        <Card><CardContent><Typography color="text.secondary" variant="body2">Catalog products</Typography>
          <Typography variant="h4">{catalog.length}</Typography></CardContent></Card>
      </Box>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ p: 2.5 }}><Typography variant="h6">Replenishment plan</Typography>
          <Typography variant="body2" color="text.secondary">PAR values take precedence; other targets use consumption history.</Typography></Box>
        <Table size="small"><TableHead><TableRow><TableCell>Product</TableCell><TableCell>GTIN</TableCell>
          <TableCell align="right">On hand</TableCell><TableCell align="right">Target</TableCell>
          <TableCell align="right">Order qty</TableCell><TableCell>Basis</TableCell>
          <TableCell align="right">Est. cost</TableCell></TableRow></TableHead>
          <TableBody>{reorder.map(item => <TableRow key={item.gtin}>
            <TableCell>{item.name || item.gtin}</TableCell><TableCell>{item.gtin}</TableCell>
            <TableCell align="right">{item.onHand}</TableCell>
            <TableCell align="right">{item.parProvided ? item.par : item.suggestedPar}</TableCell>
            <TableCell align="right"><Chip color="warning" size="small" label={item.suggestedOrderQty} /></TableCell>
            <TableCell>{item.parProvided ? 'Configured PAR' : 'Consumption model'}</TableCell>
            <TableCell align="right">${(item.estimatedCost || 0).toFixed(2)}</TableCell>
          </TableRow>)}</TableBody></Table>
        {!reorder.length && <EmptyState>No items currently need replenishment.</EmptyState>}
      </CardContent></Card>
      <Card><CardContent sx={{ p: 0 }}>
        <Box sx={{ p: 2.5 }}><Typography variant="h6">Catalog controls</Typography></Box>
        <Table size="small"><TableHead><TableRow><TableCell>Product</TableCell><TableCell>Manufacturer</TableCell>
          <TableCell>Category</TableCell><TableCell align="right">Unit price</TableCell>
          <TableCell align="right">PAR</TableCell><TableCell>Source</TableCell></TableRow></TableHead>
          <TableBody>{catalog.map(item => <TableRow key={item.gtin}><TableCell>{item.name}</TableCell>
            <TableCell>{item.manufacturer || '—'}</TableCell><TableCell>{item.category || '—'}</TableCell>
            <TableCell align="right">${(item.unitPrice || 0).toFixed(2)}</TableCell>
            <TableCell align="right">{item.par >= 0 ? item.par : 'Not set'}</TableCell>
            <TableCell>{item.source || '—'}</TableCell></TableRow>)}</TableBody></Table>
      </CardContent></Card>
    </Stack>
  )
}

function Settings({ state, run }) {
  const initial = state.settings || {}
  const [form, setForm] = useState(() => Object.fromEntries(
    Object.entries(initial).map(([key, value]) => [key, String(value)])))
  const set = (key, value) => setForm(current => ({ ...current, [key]: value }))
  const save = () => run(() => api.settings(form), 'Settings saved')
  const chooseFolder = () => run(async () => {
    const result = await api.chooseFolder()
    if (result.cancelled) throw new Error('Folder selection cancelled')
  }, 'Shared folder changed')
  const numberField = (key, label, helper) => <TextField size="small" type="number" label={label}
    value={form[key] ?? ''} helperText={helper} onChange={event => set(key, event.target.value)} />
  return (
    <Stack spacing={2}>
      <PageHeader title="Settings" description="Configure this workstation and inventory planning behavior."
        actions={<Button variant="contained" onClick={save}>Save settings</Button>} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Card><CardContent><Typography variant="h6" gutterBottom>Workstation</Typography>
          <Stack spacing={2}>
            <TextField size="small" label="Device ID" value={form.deviceId ?? ''}
              onChange={event => set('deviceId', event.target.value)} />
            <TextField size="small" label="Operator / team" value={form.actor ?? ''}
              onChange={event => set('actor', event.target.value)} />
            {numberField('scannerMinimumLength', 'Minimum scanner length', 'Reject shorter scanner input')}
            <Divider />
            <Typography variant="body2" color="text.secondary">Shared OneDrive folder</Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>{state.sharedRoot || 'Not configured'}</Typography>
            <Button startIcon={<FolderRounded />} variant="outlined" onClick={chooseFolder}>Change folder…</Button>
          </Stack>
        </CardContent></Card>
        <Card><CardContent><Typography variant="h6" gutterBottom>Inventory planning</Typography>
          <Stack spacing={2}>
            {numberField('staleDays', 'Stale inventory threshold (days)', 'Flags stock with no recent activity')}
            {numberField('reorderWindowDays', 'Usage history window (days)', 'Period used to calculate average consumption')}
            {numberField('reorderLeadDays', 'Supplier lead time (days)', 'Expected delivery lead time')}
            {numberField('reorderSafetyDays', 'Safety stock (days)', 'Additional usage buffer')}
            {numberField('reorderCoverageDays', 'Order coverage (days)', 'Target stock coverage after replenishment')}
          </Stack>
        </CardContent></Card>
        <Card><CardContent><Typography variant="h6" gutterBottom>FDA AccessGUDID</Typography>
          <FormControlLabel control={<Switch checked={form.gudidEnabled === 'true'}
            onChange={event => set('gudidEnabled', String(event.target.checked))} />}
            label="Use GUDID suggestions for unknown products" />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1, wordBreak: 'break-all' }}>
            {initial.gudidEndpoint}
          </Typography>
        </CardContent></Card>
      </Box>
    </Stack>
  )
}

function Registration({ state, run }) {
  const [form, setForm] = useState({ gtin: '', name: '', manufacturer: '', category: '', unitPrice: '', par: '', notes: '' })
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))
  const lookup = async () => {
    try {
      const r = await api.gudid({ gtin: form.gtin })
      if (r.enabled && r.found) setForm(f => ({ ...f, name: r.name || f.name, manufacturer: r.manufacturer || f.manufacturer, category: r.category || f.category }))
    } catch (e) { /* offline is fine */ }
  }
  const categories = [...new Set((state.catalog || []).map(c => c.category).filter(Boolean))]
  return (
    <Stack spacing={2}>
    <PageHeader title="Product registration"
      description="Create or maintain catalog records, PAR levels, pricing, and product classification." />
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Register / update product</Typography>
      <Stack spacing={1} sx={{ maxWidth: 520 }}>
        <Stack direction="row" spacing={1}>
          <TextField fullWidth size="small" label="GTIN" value={form.gtin} onChange={e => set('gtin', e.target.value)} />
          <Button onClick={lookup}>GUDID lookup</Button>
        </Stack>
        <TextField size="small" label="Name" value={form.name} onChange={e => set('name', e.target.value)} />
        <TextField size="small" label="Manufacturer" value={form.manufacturer} onChange={e => set('manufacturer', e.target.value)} />
        <TextField size="small" label="Category" value={form.category} onChange={e => set('category', e.target.value)}
          helperText={categories.length ? `Existing: ${categories.join(', ')}` : ''} />
        <Stack direction="row" spacing={1}>
          <TextField size="small" label="Unit price" value={form.unitPrice} onChange={e => set('unitPrice', e.target.value)} />
          <TextField size="small" label="PAR (blank = none)" value={form.par} onChange={e => set('par', e.target.value)} />
        </Stack>
        <TextField size="small" label="Notes" value={form.notes} onChange={e => set('notes', e.target.value)} />
        <Button variant="contained" onClick={() => run(() => api.register({
          ...form, unitPrice: form.unitPrice || '0', par: form.par === '' ? '-1' : form.par, source: 'MANUAL'
        }), 'Saved')}>Save product</Button>
      </Stack>
    </CardContent></Card>
    </Stack>
  )
}

function Labels({ state }) {
  const [urls, setUrls] = useState({})
  const rows = (state.stock || []).filter(line => line.active).sort((a, b) =>
    (a.category || 'Uncategorized').localeCompare(b.category || 'Uncategorized')
      || (a.name || a.gtin).localeCompare(b.name || b.gtin)
      || a.lot.localeCompare(b.lot))
  const groups = rows.reduce((result, line) => {
    const category = line.category || 'Uncategorized'
    if (!result[category]) result[category] = []
    result[category].push(line)
    return result
  }, {})
  useEffect(() => {
    let cancelled = false
    const stock = (state.stock || []).filter(line => line.active)
    Promise.all(stock.map(line => QRCode.toDataURL(`MSITEM:${line.itemKey}`, { margin: 1, width: 96 })
      .then(url => [line.itemKey, url]))).then(pairs => {
        if (!cancelled) setUrls(Object.fromEntries(pairs))
      })
    return () => { cancelled = true }
  }, [state.stock])
  return (
    <Box>
      <Box className="ledger-screen-only">
        <PageHeader title="Paper count ledger"
          description="Print the complete category-sorted inventory, record counts while away from the workstation, then scan row QR codes in Inventory Count."
          actions={<Button variant="contained" onClick={() => window.print()}>Print inventory ledger</Button>} />
        <Alert severity="info" sx={{ mb: 2 }}>
          Write the physical count and variance on paper. Back at the workstation, open Inventory Count and scan each changed row.
        </Alert>
      </Box>
      <Box className="ledger-print">
        <Box sx={{ mb: 2 }}>
          <Typography variant="h5">Medical Supply Inventory Count Ledger</Typography>
          <Typography variant="body2">Printed {new Date().toLocaleString()} · {rows.length} active stock lines</Typography>
          <Typography variant="caption">Location / team: ____________________  Counter: ____________________  Date: __________</Typography>
        </Box>
        {Object.entries(groups).map(([category, lines]) => <Box key={category}
          sx={{ mb: 2, breakInside: 'avoid-page' }}>
          <Typography variant="subtitle1" fontWeight={800} sx={{ px: 1, py: .5,
            bgcolor: '#e8eaf6', border: '1px solid #9fa8da' }}>{category}</Typography>
          <Table size="small" sx={{ '& td, & th': { border: '1px solid #9ca3af', py: .4, px: .7 } }}>
            <TableHead><TableRow><TableCell sx={{ width: 64 }}>Lookup</TableCell>
              <TableCell>Product / GTIN</TableCell><TableCell>Lot</TableCell><TableCell>Expiration</TableCell>
              <TableCell align="right">System</TableCell><TableCell sx={{ width: 90 }}>Counted</TableCell>
              <TableCell sx={{ width: 80 }}>Delta</TableCell><TableCell sx={{ width: 70 }}>Initials</TableCell>
            </TableRow></TableHead>
            <TableBody>{lines.map(line => <TableRow key={line.itemKey}>
              <TableCell>{urls[line.itemKey] && <img src={urls[line.itemKey]} width="52" height="52" alt="QR lookup" />}</TableCell>
              <TableCell><Typography variant="body2" fontWeight={700}>{line.name || 'Unregistered product'}</Typography>
                <Typography variant="caption">{line.gtin}</Typography></TableCell>
              <TableCell>{line.lot || '—'}</TableCell><TableCell>{line.expirationIso || '—'}</TableCell>
              <TableCell align="right">{line.quantity}</TableCell><TableCell></TableCell>
              <TableCell></TableCell><TableCell></TableCell>
            </TableRow>)}</TableBody>
          </Table>
        </Box>)}
      </Box>
      {!rows.length && <Card><EmptyState>No active inventory is available for the ledger.</EmptyState></Card>}
    </Box>
  )
}

function Diagnostics({ state, run }) {
  return (
    <Card><CardContent>
      <Typography variant="h6" gutterBottom>Diagnostics</Typography>
      <Typography variant="body2">Shared root: {state.sharedRoot || '(not set)'}</Typography>
      <Typography variant="body2">Events: {state.eventCount} · Pending: {state.pendingCount}</Typography>
      <Typography variant="body2">GUDID enabled: {String(state.gudidEnabled)}</Typography>
      <Typography variant="body2" color="error" sx={{ whiteSpace: 'pre-wrap' }}>
        {(state.errors || []).join('\n')}
      </Typography>
    </CardContent></Card>
  )
}

createRoot(document.getElementById('root')).render(<App />)
