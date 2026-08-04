import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert, AppBar, Box, Button, Card, CardContent, Checkbox, Chip, CircularProgress, CssBaseline,
  Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControl,
  IconButton, InputLabel, List, ListItemButton, ListItemIcon, ListItemText, MenuItem,
  Select, Snackbar, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, Tabs, TextField, ThemeProvider, Toolbar, Tooltip, Typography
} from '@mui/material'
import LocalShippingRounded from '@mui/icons-material/LocalShippingRounded'
import QrCodeScannerRounded from '@mui/icons-material/QrCodeScannerRounded'
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded'
import HistoryRounded from '@mui/icons-material/HistoryRounded'
import PeopleAltRounded from '@mui/icons-material/PeopleAltRounded'
import DescriptionRounded from '@mui/icons-material/DescriptionRounded'
import AssessmentRounded from '@mui/icons-material/AssessmentRounded'
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import TroubleshootRounded from '@mui/icons-material/TroubleshootRounded'
import MoreVertRounded from '@mui/icons-material/MoreVertRounded'
import PersonAddAltRounded from '@mui/icons-material/PersonAddAltRounded'
import BlockRounded from '@mui/icons-material/BlockRounded'
import PrintRounded from '@mui/icons-material/PrintRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import MenuRounded from '@mui/icons-material/MenuRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import { api } from './api'
import { theme } from './theme'
import { ScannerCapture, recommendScannerSettings } from './scannerCapture'

const DRAWER = 248
const locations = ['Main Receiving', 'Loading Dock', 'Mailroom', 'Warehouse']
const dateTime = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })
const shortTime = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })
const nav = [
  { section: 'OPERATIONS', items: [
    ['receive', 'Receive Packages', <QrCodeScannerRounded />],
    ['release', 'Release Packages', <LocalShippingRounded />],
    ['session', 'Current Session', <Inventory2Rounded />]
  ]},
  { section: 'ACCOUNTABILITY', items: [
    ['history', 'Package History', <HistoryRounded />],
    ['recipients', 'Recipients', <PeopleAltRounded />],
    ['manifests', 'Manifests', <DescriptionRounded />],
    ['reports', 'Reports', <AssessmentRounded />],
    ['attention', 'Attention', <WarningAmberRounded />]
  ]},
  { section: 'ADMINISTRATION', items: [
    ['settings', 'Settings', <SettingsRounded />],
    ['diagnostics', 'Diagnostics', <TroubleshootRounded />]
  ]}
]

function formatDate(value, compact = false) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return compact ? shortTime.format(parsed) : dateTime.format(parsed)
}

function reportRange(period, customFrom = '', customTo = '') {
  const now = new Date()
  let from
  let to
  if (period === 'Custom') {
    const parse = value => { const [year, month, day] = value.split('-').map(Number); return new Date(year, month - 1, day) }
    from = parse(customFrom)
    to = parse(customTo)
    to.setDate(to.getDate() + 1)
  } else {
    from = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    if (period === 'Week') {
      const mondayOffset = (from.getDay() + 6) % 7
      from.setDate(from.getDate() - mondayOffset)
    } else if (period === 'Month') from = new Date(now.getFullYear(), now.getMonth(), 1)
    to = new Date(from)
    if (period === 'Day') to.setDate(to.getDate() + 1)
    if (period === 'Week') to.setDate(to.getDate() + 7)
    if (period === 'Month') to.setMonth(to.getMonth() + 1)
  }
  return { fromUtc: from.toISOString(), toUtc: to.toISOString() }
}

function App() {
  const [state, setState] = useState(null)
  const [page, setPage] = useState('receive')
  const [mobileOpen, setMobileOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [location, setLocation] = useState(locations[0])
  const [scan, setScan] = useState('')
  const [result, setResult] = useState({ state: 'ready', heading: 'Ready to scan', message: 'Scanner input is focused.' })
  const [selected, setSelected] = useState(null)
  const [releaseCandidate, setReleaseCandidate] = useState(null)
  const [dialog, setDialog] = useState(null)
  const [toast, setToast] = useState('')
  const scanRef = useRef(null)
  const setupPrompted = useRef(false)

  const refresh = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setBusy(true)
      setState(await api.state())
    } catch (error) {
      setResult({ state: 'error', heading: 'Package was not saved', message: error.message })
    } finally {
      if (!quiet) setBusy(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const timer = setInterval(() => refresh(true), 15000)
    return () => clearInterval(timer)
  }, [refresh])

  useEffect(() => {
    if (!busy && state?.configured && (page === 'receive' || page === 'release')) scanRef.current?.focus()
  }, [busy, state, page, dialog])

  useEffect(() => {
    if (state && !state.configured && !setupPrompted.current) {
      setupPrompted.current = true
      setDialog({ type: 'setup' })
    }
  }, [state?.configured])

  useEffect(() => {
    if (!state?.scannerSettings?.soundEnabled || !['success', 'error', 'review'].includes(result.state)) return
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext
      const context = new AudioContext()
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.frequency.value = result.state === 'success' ? 880 : 330
      gain.gain.value = 0.04
      oscillator.connect(gain); gain.connect(context.destination)
      oscillator.start(); oscillator.stop(context.currentTime + (result.state === 'success' ? 0.08 : 0.18))
      oscillator.onended = () => context.close()
    } catch (_) { /* Visual feedback remains authoritative when audio is unavailable. */ }
  }, [result.state, state?.scannerSettings?.soundEnabled])

  const choosePage = value => {
    setPage(value)
    setMobileOpen(false)
    setSelected(null)
    setReleaseCandidate(null)
    setScan('')
    setResult({ state: 'ready', heading: value === 'release' ? 'Ready to find a package' : 'Ready to scan', message: 'Scanner input is focused.' })
  }

  const updateScanInput = value => {
    setScan(value)
    if (value) setResult({ state: 'capturing', heading: 'Reading scanner…', message: 'Waiting for the complete barcode.' })
  }

  const submitScan = async (mode, confirmed = false, duplicateAction = '') => {
    if (!scan.trim()) return
    setBusy(true)
    setResult({ state: 'saving', heading: mode === 'Inbound' ? 'Saving package…' : 'Saving release…', message: 'Writing the accountability record.' })
    try {
      const response = await api.scan({ raw: scan, mode, location, recipient: '', confirmed: String(confirmed), duplicateAction, observedRevision: releaseCandidate?.revision ?? -1 })
      if (response.confirmationRequired) {
        setDialog({ type: response.confirmationType === 'duplicate' ? 'duplicate' : 'ambiguous', response, mode })
        setResult({ state: 'review', heading: 'Check this package', message: response.confirmationType === 'duplicate' ? 'This tracking number is already active.' : 'Confirm the proposed tracking number before it is saved.' })
        return
      }
      setResult({
        state: response.kind === 'WARNING' ? 'review' : 'success',
        heading: response.kind === 'WARNING' ? 'Check this package' : mode === 'Inbound' ? 'Package received' : 'Package released',
        message: response.message,
        trackingNumber: response.trackingNumber || scan.trim(),
        carrier: response.carrier,
        recipient: response.recipient,
        occurredUtc: new Date().toISOString()
      })
      setScan('')
      setReleaseCandidate(null)
      await refresh(true)
    } catch (error) {
      setResult({ state: 'error', heading: 'Package was not saved', message: error.message })
    } finally {
      setBusy(false)
    }
  }

  const lookupRelease = async () => {
    if (!scan.trim()) return
    setBusy(true)
    setResult({ state: 'saving', heading: 'Finding package…', message: 'Checking the current accountability record.' })
    try {
      const candidate = await api.lookup({ raw: scan })
      setReleaseCandidate(candidate)
      setResult({ state: candidate.canRelease ? 'ready' : 'review', heading: candidate.canRelease ? 'Verify this package' : 'Check this package', message: candidate.canRelease ? 'Confirm the recipient and package before recording custody transfer.' : candidate.blockReason })
    } catch (error) {
      setReleaseCandidate(null)
      setResult({ state: 'error', heading: 'Package was not found', message: error.message })
    } finally { setBusy(false) }
  }

  const configure = async path => {
    setBusy(true)
    try {
      await api.configure({ sharedRoot: path })
      setDialog(null)
      setToast('Workstation folder configured')
      await refresh()
    } catch (error) {
      setToast(error.message)
    } finally {
      setBusy(false)
    }
  }

  const assign = async recipient => {
    const target = dialog?.package || selected
    try {
      await api.assignRecipient({ trackingNumber: target.trackingNumber, recipient })
      setDialog(null); setToast('Recipient assigned'); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const bulkAssign = async (recipient, targets) => {
    try {
      const response = await api.assignRecipients({ recipient, trackingNumbers: targets.map(item => item.trackingNumber).join('|') })
      setDialog(null); setToast(response.message); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const voidPackage = async reason => {
    try {
      await api.voidPackage({ trackingNumber: selected.trackingNumber, reason })
      setDialog(null); setSelected(null); setToast('Package void recorded'); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const correctPackage = async values => {
    try {
      await api.correctPackage({ trackingNumber: selected.trackingNumber, observedRevision: selected.revision, ...values })
      setDialog(null); setSelected(null); setToast('Package correction recorded'); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const resolveConflict = async values => {
    try {
      await api.resolveConflict(values)
      setDialog(null); setToast('Conflict resolution recorded'); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const finishSession = async (closeWithoutManifest = false) => {
    try {
      const response = await api.finishSession({ closeWithoutManifest: String(closeWithoutManifest) })
      if (response.confirmationRequired) setDialog({ type: 'finish', count: response.unmanifestedCount })
      else { setDialog(null); setSelected(null); setToast(response.message); await refresh(true) }
    } catch (error) { setToast(error.message) }
  }

  const packages = state?.packages || []
  const session = state?.session || []
  const activity = state?.activity || []
  const availableLocations = (state?.sharedSettings?.locations || locations.join('|')).split('|').map(value => value.trim()).filter(Boolean)
  useEffect(() => {
    if (state && availableLocations.length && !availableLocations.includes(location)) {
      setLocation(availableLocations[0])
    } else if (state?.defaultLocation && availableLocations.includes(state.defaultLocation) && location === locations[0]) {
      setLocation(state.defaultLocation)
    }
  }, [state?.sharedSettings?.locations])
  const attentionCount = (state?.conflicts?.length || 0) + (state?.errors?.length || 0) + (state?.warnings?.length || 0) + (state?.attention?.length || 0)
  const unassigned = packages.filter(item => item.status === 'READY_FOR_PICKUP' && !item.recipient)
  const badges = { session: session.length, recipients: unassigned.length, attention: attentionCount }
  const storage = attentionCount ? 'Attention needed' : state?.configured ? 'Submitted to shared folder' : 'Setup required'
  const title = nav.flatMap(group => group.items).find(item => item[0] === page)?.[1] || 'Commercial Tracking'

  if (!state) return <Box sx={{ display: 'grid', placeItems: 'center', height: '100vh' }}><CircularProgress aria-label="Loading Commercial Tracking" /></Box>

  const drawer = <Navigation page={page} badges={badges} onChoose={choosePage} />

  return <Box sx={{ display: 'flex', minHeight: '100vh' }}>
    <CssBaseline />
    <AppBar position="fixed" color="inherit" elevation={0} sx={{ zIndex: theme.zIndex.drawer + 1, borderBottom: '1px solid', borderColor: 'divider' }}>
      <Toolbar>
        <IconButton aria-label="Open navigation" onClick={() => setMobileOpen(true)} sx={{ display: { md: 'none' }, mr: 1 }}><MenuRounded /></IconButton>
        <LocalShippingRounded color="primary" sx={{ mr: 1.5 }} />
        <Typography variant="h6" sx={{ minWidth: { md: DRAWER - 32 }, color: 'primary.dark' }}>Commercial Tracking</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ display: { xs: 'none', sm: 'block' }, flexGrow: 1 }}>{title}</Typography>
        <Chip size="small" color={attentionCount ? 'warning' : state.configured ? 'success' : 'default'} variant="outlined" label={storage} />
        <Tooltip title="Application menu"><IconButton aria-label="Application menu" onClick={() => setDialog({ type: 'menu' })} sx={{ ml: 1 }}><MoreVertRounded /></IconButton></Tooltip>
      </Toolbar>
    </AppBar>
    <Drawer variant="permanent" sx={{ display: { xs: 'none', md: 'block' }, width: DRAWER, '& .MuiDrawer-paper': { width: DRAWER, mt: '64px', height: 'calc(100% - 64px)' } }}>{drawer}</Drawer>
    <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)} sx={{ display: { md: 'none' }, '& .MuiDrawer-paper': { width: DRAWER } }}>{drawer}</Drawer>
    <Box component="main" sx={{ flexGrow: 1, minWidth: 0, mt: '64px', p: { xs: 2, lg: 3 } }}>
      {page === 'receive' && <ScanWorkspace mode="Inbound" state={state} scannerSettings={state.scannerSettings} locations={availableLocations} location={location} setLocation={value => { setLocation(value); api.preferences({ ...state.scannerSettings, deviceId: state.deviceId, defaultLocation: value }).then(() => refresh(true)).catch(error => setToast(error.message)) }} scan={scan} setScan={updateScanInput} result={result} busy={busy} scanRef={scanRef} submit={() => submitScan('Inbound')} recent={session} onAssign={item => setDialog({ type: 'recipient', package: item })} />}
      {page === 'release' && <ScanWorkspace mode="Outbound" state={state} scannerSettings={state.scannerSettings} location={location} scan={scan} setScan={value => { updateScanInput(value); setReleaseCandidate(null) }} result={result} busy={busy} scanRef={scanRef} submit={lookupRelease} candidate={releaseCandidate} onConfirmRelease={() => submitScan('Outbound', true)} recent={packages.filter(item => item.status === 'PICKED_UP')} />}
      {page === 'session' && <SessionWorkspace rows={session} activity={state.sessionActivity || []} selected={selected} setSelected={item => setSelected(item ? { ...item, activity: activity.filter(event => event.trackingNumber === item.trackingNumber) } : null)} onFinish={() => finishSession(false)} />}
      {page === 'history' && <HistoryWorkspace rows={packages} activity={activity} selected={selected} setSelected={setSelected} />}
      {page === 'recipients' && <RecipientWorkspace rows={unassigned} onAssign={targets => setDialog({ type: 'bulkRecipient', packages: targets })} />}
      {page === 'manifests' && <ManifestWorkspace manifests={state.manifests || []} session={session} packages={packages} location={location} onFinalize={async payload => { try { const response = await api.manifest(payload); setToast(`Manifest finalized: ${response.manifestId}`); await refresh(true) } catch (error) { setToast(error.message) } }} onReprint={async manifestId => { try { const response = await api.reprintManifest({ manifestId }); setToast(response.message) } catch (error) { setToast(error.message) } }} />}
      {page === 'reports' && <ReportsWorkspace operationalZone={state.sharedSettings?.operationalTimeZone} events={activity} packages={packages} onCreate={async payload => { try { const response = await api.report(payload); setToast(`${response.count} rows exported to ${response.csvFile}`) } catch (error) { setToast(error.message) } }} />}
      {page === 'attention' && <AttentionWorkspace conflicts={state.conflicts} errors={state.errors} notices={[...(state.attention || []), ...(state.warnings || [])]} packages={packages} onResolve={item => setDialog({ type: 'conflict', package: item })} onRetry={async () => { const response = await api.retryPending(); setToast(response.message); await refresh(true) }} />}
      {page === 'settings' && <SettingsWorkspace state={state} onConfigure={() => setDialog({ type: 'setup' })} onSaveScanner={async values => { try { await api.preferences(values); setToast('Scanner settings saved'); await refresh(true) } catch (error) { setToast(error.message) } }} onSaveShared={async values => { try { const response = await api.saveSharedSettings({ ...values, confirmed: 'true' }); setToast(response.message); await refresh(true) } catch (error) { setToast(error.message) } }} onRollbackShared={async () => { try { const response = await api.rollbackSharedSettings(); setToast(response.message); await refresh(true) } catch (error) { setToast(error.message) } }} />}
      {page === 'diagnostics' && <Diagnostics state={state} onRebuild={async () => { const response = await api.rebuildProjection(); setToast(response.message); await refresh(true) }} onExport={async () => { const response = await api.exportDiagnostics(); setToast(`${response.message} ${response.file}`) }} />}
    </Box>
    {selected && <DetailPanel item={selected} onClose={() => setSelected(null)} onAssign={() => setDialog({ type: 'recipient' })} onCorrect={() => setDialog({ type: 'correction', package: selected })} onVoid={() => setDialog({ type: 'void' })} />}
    <ActionDialog dialog={dialog} state={state} locations={availableLocations} busy={busy} onClose={() => setDialog(null)} onConfigure={configure} onConfirm={() => { setDialog(null); submitScan(dialog.mode, true) }} onDuplicate={action => { setDialog(null); submitScan('Inbound', false, action) }} onAssign={assign} onBulkAssign={bulkAssign} onCorrect={correctPackage} onResolve={resolveConflict} onVoid={voidPackage} onNavigate={choosePage} onFinishWithout={() => finishSession(true)} />
    <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast('')} message={toast} />
  </Box>
}

function Navigation({ page, badges, onChoose }) {
  return <Box component="nav" aria-label="Primary navigation" sx={{ py: 2 }}>
    {nav.map(group => <Box key={group.section} sx={{ mb: 2 }}>
      <Typography variant="overline" color="text.secondary" sx={{ px: 2.5 }}>{group.section}</Typography>
      <List dense>{group.items.map(([key, label, icon]) => <ListItemButton key={key} selected={page === key} onClick={() => onChoose(key)}>
        <ListItemIcon>{icon}</ListItemIcon><ListItemText primary={label} />
        {!!badges[key] && <Chip size="small" label={badges[key]} color={key === 'attention' ? 'warning' : 'default'} />}
      </ListItemButton>)}</List>
    </Box>)}
  </Box>
}

function PageHeader({ title, instruction, action }) {
  return <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'flex-start' }} spacing={2} sx={{ mb: 2.5 }}>
    <Box><Typography variant="h4">{title}</Typography><Typography color="text.secondary" sx={{ mt: .5 }}>{instruction}</Typography></Box>{action}
  </Stack>
}

function ScanWorkspace({ mode, state, scannerSettings, locations, location, setLocation, scan, setScan, result, busy, scanRef, submit, recent, onAssign, candidate, onConfirmRelease }) {
  const inbound = mode === 'Inbound'
  const severity = { success: 'success', review: 'warning', error: 'error', saving: 'info', capturing: 'info', ready: 'info' }[result.state]
  const capture = useRef(new ScannerCapture(scannerSettings))
  useEffect(() => { capture.current = new ScannerCapture(scannerSettings) }, [scannerSettings])
  const idleTimer = useRef(null)
  const pasted = useRef(false)
  const complete = () => {
    if (capture.current.accept(scan.trim(), performance.now())) submit()
  }
  useEffect(() => {
    clearTimeout(idleTimer.current)
    if (!busy && pasted.current && scan.length >= capture.current.settings.minimumLength) {
      pasted.current = false
      idleTimer.current = setTimeout(complete, 0)
      return () => clearTimeout(idleTimer.current)
    }
    if (!busy && scan.length >= capture.current.settings.minimumLength) {
      idleTimer.current = setTimeout(() => {
        if (capture.current.shouldCompleteAfterIdle(performance.now(), scan)) complete()
      }, capture.current.completionDelayMs(scan))
    }
    return () => clearTimeout(idleTimer.current)
  }, [scan, busy])
  const captureKey = event => {
    if (capture.current.isTerminator(event.key) && scan.trim()) {
      event.preventDefault()
      clearTimeout(idleTimer.current)
      if (capture.current.shouldCompleteForTerminator(scan)) complete()
      return
    }
    if (event.key.length !== 1) return
    capture.current.character(performance.now(), scan.length)
  }
  return <Box sx={{ maxWidth: 1080, mx: 'auto' }}>
    <PageHeader title={inbound ? 'Receive Packages' : 'Release Packages'} instruction={inbound ? 'Confirm the location, then scan each package.' : 'Find the package, verify its details, then confirm custody transfer.'} />
    {!state.configured && <Alert severity="warning" sx={{ mb: 2 }}>Complete workstation setup in Settings before scanning.</Alert>}
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ p: { xs: 2, md: 3 } }}>
        {inbound && <FormControl size="small" sx={{ minWidth: 240, mb: 2 }}><InputLabel id="location-label">Receiving location</InputLabel><Select labelId="location-label" label="Receiving location" value={location} onChange={event => setLocation(event.target.value)}>{locations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
        <Box component="form" onSubmit={event => { event.preventDefault(); submit() }}>
          <TextField inputRef={scanRef} fullWidth autoComplete="off" disabled={busy || !state.configured} value={scan}
            onKeyDown={captureKey} onPaste={event => { pasted.current = capture.current.paste(performance.now(), event.clipboardData.getData('text')) }}
            onChange={event => { if (event.target.value.length <= scan.length) capture.current.edit(); setScan(event.target.value) }}
            label={inbound ? 'Scan package barcode' : 'Scan or enter tracking number'} placeholder="Scanner input appears here"
            inputProps={{ 'aria-describedby': 'scan-help' }} InputProps={{ sx: { fontFamily: 'monospace', fontSize: 20, minHeight: 64 } }} />
          <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography id="scan-help" variant="caption" color="text.secondary">Press Enter or use Process for manual input. Scanner focus returns after each action.</Typography><Button type="submit" disabled={!scan.trim() || busy}>Process</Button></Stack>
        </Box>
      </CardContent>
    </Card>
    <Alert role="status" aria-live={result.state === 'error' ? 'assertive' : 'polite'} severity={severity} icon={result.state === 'saving' ? <CircularProgress size={22} /> : result.state === 'success' ? <CheckCircleRounded /> : undefined} sx={{ mb: 2, py: 1.5 }}>
      <Typography variant="h6">{result.heading}</Typography><Typography>{result.message}</Typography>
      {result.trackingNumber && <Typography sx={{ mt: .5, fontFamily: 'monospace', fontWeight: 700 }}>{result.carrier || 'Package'} · {result.trackingNumber}</Typography>}
      {result.occurredUtc && <Typography variant="body2" sx={{ mt: .5 }}>{location} · {formatDate(result.occurredUtc)}</Typography>}
      {result.trackingNumber && <Typography variant="body2">Recipient: {result.recipient || 'Unassigned'}</Typography>}
      {(result.trackingNumber || result.state === 'error') && <Button size="small" sx={{ mt: 1 }} onClick={() => navigator.clipboard?.writeText(result.trackingNumber || result.message)}>Copy {result.trackingNumber ? 'tracking number' : 'error details'}</Button>}
    </Alert>
    {!inbound && candidate && <Card sx={{ mb: 2, borderColor: candidate.canRelease ? 'primary.main' : 'warning.main' }}><CardContent>
      <Typography variant="overline" color="text.secondary">Package verification</Typography>
      <Typography variant="h5" sx={{ fontFamily: 'monospace', mb: 2 }}>{candidate.trackingNumber}</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4}><Detail label="Recipient" value={<Typography variant="h6">{candidate.recipient || 'Unassigned'}</Typography>} /><Detail label="Carrier" value={candidate.carrier} /><Detail label="Received location" value={candidate.location} /><Detail label="Status" value={<StatusChip status={candidate.status} />} /></Stack>
      <Button variant="contained" size="large" sx={{ mt: 3 }} disabled={!candidate.canRelease || busy} onClick={onConfirmRelease}>Confirm release</Button>
    </CardContent></Card>}
    <Card variant="outlined"><CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="h6">{inbound ? 'Current session' : 'Recently released'} · {recent.length} packages</Typography></Stack>
      <RecentRows rows={recent.slice(0, 5)} onAssign={onAssign} />
    </CardContent></Card>
  </Box>
}

function RecentRows({ rows, onAssign }) {
  if (!rows.length) return <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>Packages will appear here after they are recorded.</Typography>
  return <Stack divider={<Divider />} sx={{ mt: 1 }}>{rows.map((row, index) => <Stack key={`${row.trackingNumber}-${index}`} direction="row" alignItems="center" spacing={2} sx={{ py: 1.25 }}>
    <Typography variant="body2" color="text.secondary" sx={{ width: 76 }}>{formatDate(row.lastEventUtc || row.occurredUtc, true)}</Typography>
    <Typography sx={{ width: 64 }}>{row.carrier || 'Other'}</Typography>
    <Typography sx={{ fontFamily: 'monospace', fontWeight: 700, flexGrow: 1 }}>{row.trackingNumber}</Typography>
    <Typography color={row.recipient ? 'text.primary' : 'text.secondary'}>{row.recipient || 'Unassigned'}</Typography>
    {onAssign && !row.recipient && <Button size="small" onClick={() => onAssign(row)}>Assign</Button>}
  </Stack>)}</Stack>
}

function SessionWorkspace({ rows, activity, selected, setSelected, onFinish }) {
  const [tab, setTab] = useState(0)
  const [query, setQuery] = useState('')
  const filtered = rows.filter(row => Object.values(row).some(value => String(value || '').toLowerCase().includes(query.toLowerCase())))
  return <><PageHeader title="Current Session" instruction="Review one current-state row per package and finish the receiving batch." action={<Button variant="contained" onClick={onFinish} disabled={!rows.length}>Finish receiving session</Button>} />
    <TextField fullWidth value={query} onChange={event => setQuery(event.target.value)} placeholder="Search this session by tracking, carrier, recipient, location, status, or notes" InputProps={{ startAdornment: <SearchRounded color="action" sx={{ mr: 1 }} /> }} sx={{ mb: 2 }} />
    <Card><Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 2 }}><Tab label={`Packages (${rows.length})`} /><Tab label={`Session activity (${activity.length})`} /></Tabs><Divider />
      {tab === 0 ? <PackageTable rows={filtered} selected={selected} onSelect={setSelected} /> : <ActivityTable rows={activity.filter(row => Object.values(row).some(value => String(value || '').toLowerCase().includes(query.toLowerCase())))} />}
    </Card></>
}

function HistoryWorkspace({ rows, activity, selected, setSelected }) {
  const [query, setQuery] = useState('')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [filters, setFilters] = useState({ status: '', location: '', carrier: '', from: '', to: '' })
  const filtered = rows.filter(row => {
    const searched = Object.values(row).some(value => String(value || '').toLowerCase().includes(query.toLowerCase()))
    const recorded = new Date(row.lastEventUtc || 0)
    return searched && (!filters.status || row.status === filters.status) && (!filters.location || row.location === filters.location)
      && (!filters.carrier || row.carrier === filters.carrier)
      && (!filters.from || recorded >= new Date(`${filters.from}T00:00:00`))
      && (!filters.to || recorded <= new Date(`${filters.to}T23:59:59.999`))
  })
  const update = (key, value) => setFilters(current => ({ ...current, [key]: value }))
  return <><PageHeader title="Package History" instruction="Search projected package records and open the complete accountability detail." />
    <Stack direction="row" spacing={1} sx={{ mb: 2 }}><TextField fullWidth value={query} onChange={event => setQuery(event.target.value)} placeholder="Search tracking, manifest, carrier, recipient, location, status, or notes" InputProps={{ startAdornment: <SearchRounded color="action" sx={{ mr: 1 }} /> }} /><Button onClick={() => setFiltersOpen(value => !value)}>Filters</Button></Stack>
    {filtersOpen && <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}><TextField select label="Status" value={filters.status} onChange={event => update('status', event.target.value)} sx={{ minWidth: 170 }}><MenuItem value="">All</MenuItem>{['READY_FOR_PICKUP', 'PICKED_UP', 'VOIDED', 'CONFLICT'].map(value => <MenuItem key={value} value={value}>{value.replaceAll('_', ' ')}</MenuItem>)}</TextField><TextField select label="Location" value={filters.location} onChange={event => update('location', event.target.value)} sx={{ minWidth: 180 }}><MenuItem value="">All</MenuItem>{locations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField><TextField label="Carrier" value={filters.carrier} onChange={event => update('carrier', event.target.value)} /><TextField type="date" label="From" InputLabelProps={{ shrink: true }} value={filters.from} onChange={event => update('from', event.target.value)} /><TextField type="date" label="To" InputLabelProps={{ shrink: true }} value={filters.to} onChange={event => update('to', event.target.value)} /></Stack>}
    <Card><PackageTable rows={filtered} selected={selected} onSelect={item => setSelected({ ...item, activity: activity.filter(event => event.trackingNumber === item.trackingNumber) })} /></Card></>
}

function TableWorkspace({ title, instruction, rows, selected, setSelected, primary }) {
  return <><PageHeader title={title} instruction={instruction} action={primary} /><Card><PackageTable rows={rows} selected={selected} onSelect={setSelected} /></Card></>
}

function RecipientWorkspace({ rows, onAssign }) {
  const [chosen, setChosen] = useState([])
  const toggle = row => setChosen(current => current.some(item => item.trackingNumber === row.trackingNumber) ? current.filter(item => item.trackingNumber !== row.trackingNumber) : [...current, row])
  return <><PageHeader title="Recipient reconciliation" instruction="Select active unassigned packages, review the count, and assign the correct recipient." action={<Button variant="contained" disabled={!chosen.length} onClick={() => onAssign(chosen)}>Review assignment ({chosen.length})</Button>} />
    <Card>{rows.length ? <TableContainer><Table><TableHead><TableRow><TableCell padding="checkbox"><Checkbox aria-label="Select all packages" checked={chosen.length === rows.length} indeterminate={chosen.length > 0 && chosen.length < rows.length} onChange={event => setChosen(event.target.checked ? rows : [])} /></TableCell><TableCell>Received</TableCell><TableCell>Tracking number</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell></TableRow></TableHead><TableBody>{rows.map(row => <TableRow key={row.trackingNumber} hover><TableCell padding="checkbox"><Checkbox aria-label={`Select ${row.trackingNumber}`} checked={chosen.some(item => item.trackingNumber === row.trackingNumber)} onChange={() => toggle(row)} /></TableCell><TableCell>{formatDate(row.lastEventUtc)}</TableCell><TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{row.trackingNumber}</TableCell><TableCell>{row.carrier}</TableCell><TableCell>{row.location}</TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState />}</Card></>
}

function PackageTable({ rows, selected, onSelect }) {
  if (!rows.length) return <EmptyState />
  return <TableContainer sx={{ maxHeight: 'calc(100vh - 220px)' }}><Table stickyHeader><TableHead><TableRow><TableCell>Received</TableCell><TableCell>Tracking number</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell><TableCell>Status</TableCell></TableRow></TableHead><TableBody>
    {rows.map((row, index) => <TableRow hover tabIndex={0} key={`${row.trackingNumber}-${index}`} selected={selected?.trackingNumber === row.trackingNumber} onClick={() => onSelect(row)} onKeyDown={event => event.key === 'Enter' && onSelect(row)} sx={{ cursor: 'pointer' }}>
      <TableCell>{formatDate(row.lastEventUtc || row.occurredUtc)}</TableCell><TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{row.trackingNumber}</TableCell><TableCell>{row.carrier || 'Other'}</TableCell><TableCell>{row.location || '—'}</TableCell><TableCell>{row.recipient || 'Unassigned'}</TableCell><TableCell><StatusChip status={row.status} /></TableCell>
    </TableRow>)}
  </TableBody></Table></TableContainer>
}

function ActivityTable({ rows }) {
  const labels = { PACKAGE_RECEIVED: 'Received', RECIPIENT_ASSIGNED: 'Recipient assigned', PACKAGE_LOCATION_CHANGED: 'Location changed', PACKAGE_RELEASED: 'Released', PACKAGE_VOIDED: 'Voided' }
  if (!rows.length) return <EmptyState />
  return <TableContainer><Table><TableHead><TableRow><TableCell>Recorded</TableCell><TableCell>Activity</TableCell><TableCell>Tracking number</TableCell><TableCell>Operator</TableCell><TableCell>Workstation</TableCell></TableRow></TableHead><TableBody>
    {rows.map(row => <TableRow key={row.eventId}><TableCell title={row.occurredUtc}>{formatDate(row.occurredUtc)}</TableCell><TableCell>{labels[row.eventType] || row.eventType}</TableCell><TableCell sx={{ fontFamily: 'monospace' }}>{row.trackingNumber}</TableCell><TableCell>{row.actor}</TableCell><TableCell>{row.deviceId}</TableCell></TableRow>)}
  </TableBody></Table></TableContainer>
}

function StatusChip({ status }) {
  const label = (status || 'UNKNOWN').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase())
  const color = status === 'CONFLICT' || status === 'VOIDED' ? 'error' : status === 'READY_FOR_PICKUP' ? 'success' : 'default'
  return <Chip size="small" variant="outlined" color={color} label={label} />
}

function DetailPanel({ item, onClose, onAssign, onCorrect, onVoid }) {
  return <Drawer anchor="right" open onClose={onClose} sx={{ '& .MuiDrawer-paper': { width: { xs: '100%', sm: 430 }, p: 3 } }}>
    <Typography variant="overline" color="text.secondary">Package detail</Typography><Typography variant="h5" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>{item.trackingNumber}</Typography>
    <Divider sx={{ my: 2 }} /><Stack spacing={2}><Detail label="Carrier" value={item.carrier} /><Detail label="Recipient" value={item.recipient || 'Unassigned'} /><Detail label="Location" value={item.location} /><Detail label="Status" value={<StatusChip status={item.status} />} /><Detail label="Last activity" value={formatDate(item.lastEventUtc || item.occurredUtc)} /></Stack>
    {!!item.activity?.length && <Box sx={{ mt: 3 }}><Typography variant="h6">Activity timeline</Typography><Stack sx={{ mt: 1 }} divider={<Divider />}>{item.activity.map(event => <Box key={event.eventId} sx={{ py: 1.25 }}><Typography fontWeight={500}>{(event.eventType || '').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase())}</Typography><Typography variant="body2" color="text.secondary">{formatDate(event.occurredUtc)} · {event.actor} · {event.deviceId}</Typography><Typography variant="caption" color="text.secondary" title={event.eventId}>Revision observed: {event.observedRevision} · UTC: {event.occurredUtc}</Typography></Box>)}</Stack></Box>}
    <Divider sx={{ my: 3 }} /><Stack direction="row" spacing={1}><Button startIcon={<PersonAddAltRounded />} onClick={onAssign}>Assign recipient</Button><Button startIcon={<EditRounded />} onClick={onCorrect}>Correct package…</Button></Stack>
    <Box sx={{ mt: 'auto', pt: 4 }}><Divider sx={{ mb: 2 }} /><Typography variant="subtitle2" color="error" sx={{ mb: 1 }}>Danger area</Typography><Button color="error" variant="outlined" startIcon={<BlockRounded />} onClick={onVoid}>Void package…</Button></Box>
  </Drawer>
}

function Detail({ label, value }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Box>{value || '—'}</Box></Box>
}

function InfoWorkspace({ title, instruction, icon, action, text }) {
  return <><PageHeader title={title} instruction={instruction} action={action} /><Card><CardContent sx={{ py: 7, textAlign: 'center' }}><Box sx={{ color: 'primary.main', '& svg': { fontSize: 48 } }}>{icon}</Box><Typography color="text.secondary" sx={{ mt: 1 }}>{text}</Typography></CardContent></Card></>
}

function ManifestWorkspace({ manifests, session, packages, location, onFinalize, onReprint }) {
  const [tab, setTab] = useState(0)
  const [type, setType] = useState('inbound')
  const [manifestLocation, setManifestLocation] = useState(location)
  const sessionLocations = [...new Set(session.map(item => item.location).filter(Boolean))]
  const eligible = type === 'inbound' ? session.filter(item => !item.manifestId && item.location === manifestLocation) : packages.filter(item => item.status === 'READY_FOR_PICKUP' && item.recipient)
  const [chosen, setChosen] = useState([])
  useEffect(() => setChosen(type === 'inbound' ? eligible : []), [type, manifestLocation, session.length, manifests.length])
  const toggle = item => setChosen(current => current.some(value => value.trackingNumber === item.trackingNumber) ? current.filter(value => value.trackingNumber !== item.trackingNumber) : [...current, item])
  const custodyRecipients = [...new Set(packages.filter(item => item.status === 'READY_FOR_PICKUP' && item.recipient).map(item => item.recipient))]
  const [custodyRecipient, setCustodyRecipient] = useState('')
  const proposeId = () => {
    const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
    const suffix = crypto.randomUUID().replaceAll('-', '').slice(0, 6).toUpperCase()
    return `MNF-${stamp}-${suffix}`
  }
  const [manifestId, setManifestId] = useState(proposeId)
  useEffect(() => setManifestId(proposeId()), [manifests.length])
  useEffect(() => {
    if (type === 'custody' && custodyRecipient) setChosen(eligible.filter(item => item.recipient === custodyRecipient))
  }, [custodyRecipient, type])
  return <><PageHeader title="Manifests" instruction="Prepare audited package lists and review finalized manifests." />
    <Card><Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 2 }}><Tab label="Prepare manifest" /><Tab label={`Manifest register (${manifests.length})`} /></Tabs><Divider />
      {tab === 0 ? <CardContent><Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormControl sx={{ minWidth: 220 }}><InputLabel>Purpose</InputLabel><Select label="Purpose" value={type} onChange={event => setType(event.target.value)}><MenuItem value="inbound">Inbound receiving</MenuItem><MenuItem value="custody">Recipient custody</MenuItem></Select></FormControl>{type === 'inbound' && <FormControl sx={{ minWidth: 220 }}><InputLabel>Location</InputLabel><Select label="Location" value={manifestLocation} onChange={event => setManifestLocation(event.target.value)}>{sessionLocations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}{type === 'custody' && <FormControl sx={{ minWidth: 240 }}><InputLabel>Recipient</InputLabel><Select label="Recipient" value={custodyRecipient} onChange={event => setCustodyRecipient(event.target.value)}>{custodyRecipients.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}</Stack><Typography color="text.secondary" sx={{ my: 2 }}>Review exact membership before finalization. Finalization writes immutable audit events even if printing is canceled.</Typography>
        <TableContainer sx={{ maxHeight: 330 }}><Table size="small"><TableHead><TableRow><TableCell padding="checkbox"></TableCell><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell></TableRow></TableHead><TableBody>{eligible.filter(item => type === 'inbound' || !custodyRecipient || item.recipient === custodyRecipient).map(item => <TableRow key={item.trackingNumber}><TableCell padding="checkbox"><Checkbox checked={chosen.some(value => value.trackingNumber === item.trackingNumber)} onChange={() => toggle(item)} /></TableCell><TableCell sx={{ fontFamily: 'monospace' }}>{item.trackingNumber}</TableCell><TableCell>{item.carrier}</TableCell><TableCell>{item.location}</TableCell><TableCell>{item.recipient || 'Unassigned'}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
        <Typography sx={{ my: 1 }}>{chosen.length} included · {Math.max(0, eligible.length - chosen.length)} excluded</Typography><Typography variant="body2">Proposed manifest ID: <b>{manifestId}</b> · Prepared: {formatDate(new Date().toISOString())}</Typography>{chosen.length > 100 && <Alert severity="warning" sx={{ my: 2 }}>Audited manifests are limited to 100 packages. Split this selection.</Alert>}<Button variant="contained" startIcon={<PrintRounded />} disabled={!chosen.length || chosen.length > 100} onClick={() => onFinalize({ type, location: manifestLocation, manifestId, trackingNumbers: chosen.map(item => item.trackingNumber).join('|') })}>Finalize and open print view</Button></CardContent>
        : manifests.length ? <TableContainer><Table><TableHead><TableRow><TableCell>Manifest ID</TableCell><TableCell>Type</TableCell><TableCell>Location/recipient</TableCell><TableCell>Prepared</TableCell><TableCell>Packages</TableCell><TableCell>Checksum</TableCell><TableCell></TableCell></TableRow></TableHead><TableBody>{manifests.map(item => <TableRow key={item.manifestId}><TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{item.manifestId}</TableCell><TableCell>{item.type}</TableCell><TableCell>{item.location}</TableCell><TableCell>{formatDate(item.preparedUtc)}</TableCell><TableCell>{item.count}</TableCell><TableCell sx={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }} title={item.checksum}>{item.checksum || 'Pending'}</TableCell><TableCell><Button size="small" onClick={() => onReprint(item.manifestId)}>Reprint</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState />}
    </Card></>
}

function ReportsWorkspace({ onCreate, operationalZone, events, packages }) {
  const [type, setType] = useState('Receiving Activity')
  const [range, setRange] = useState('Day')
  const today = new Date().toISOString().slice(0, 10)
  const [customFrom, setCustomFrom] = useState(today)
  const [customTo, setCustomTo] = useState(today)
  const zone = operationalZone || Intl.DateTimeFormat().resolvedOptions().timeZone
  const [bounds, setBounds] = useState(reportRange(range, customFrom, customTo))
  const [filters, setFilters] = useState({ location: '', carrier: '', status: '', recipient: '' })
  const reportColumns = [['time', 'Occurred time'], ['tracking', 'Tracking number'], ['carrier', 'Carrier'], ['recipient', 'Recipient'], ['location', 'Location'], ['status', 'Status'], ['manifest', 'Manifest ID'], ['actor', 'Windows account'], ['device', 'Workstation']]
  const [columns, setColumns] = useState(reportColumns.map(item => item[0]))
  const [groupBy, setGroupBy] = useState('location')
  const [sortOrder, setSortOrder] = useState('occurred-asc')
  const [includeSummary, setIncludeSummary] = useState('true')
  const updateFilter = (key, value) => setFilters(current => ({ ...current, [key]: value }))
  useEffect(() => {
    api.reportRange({ period: range.toLowerCase(), timeZone: zone, fromDate: customFrom, toDate: customTo })
      .then(setBounds).catch(() => setBounds(reportRange(range, customFrom, customTo)))
  }, [range, zone, customFrom, customTo])
  const preview = useMemo(() => events.filter(event => {
    const packageState = packages.find(item => item.trackingNumber === event.trackingNumber) || {}
    const expectedType = type.startsWith('Outbound') ? 'PACKAGE_RELEASED' : 'PACKAGE_RECEIVED'
    return event.eventType === expectedType && event.occurredUtc >= bounds.fromUtc && event.occurredUtc < bounds.toUtc
      && (!filters.location || event.location === filters.location)
      && (!filters.carrier || event.carrier.toLowerCase() === filters.carrier.toLowerCase())
      && (!filters.status || packageState.status === filters.status)
      && (!filters.recipient || (packageState.recipient || '').toLowerCase().includes(filters.recipient.toLowerCase()))
  }), [events, packages, type, bounds, filters])
  return <><PageHeader title="Reports" instruction="Create on-demand accountability extracts without changing package records." />
    <Alert severity="info" sx={{ mb: 2 }}>Reporting extract — creating or printing this list does not change package records.</Alert>
    <Card><CardContent><Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <FormControl sx={{ minWidth: 240 }}><InputLabel>Report type</InputLabel><Select label="Report type" value={type} onChange={event => setType(event.target.value)}><MenuItem value="Receiving Activity">Receiving Activity</MenuItem><MenuItem value="Outbound/Custody Activity">Outbound/Custody Activity</MenuItem></Select></FormControl>
      <FormControl sx={{ minWidth: 160 }}><InputLabel>Range</InputLabel><Select label="Range" value={range} onChange={event => setRange(event.target.value)}>{['Day', 'Week', 'Month', 'Custom'].map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>
      {range === 'Custom' && <><TextField type="date" label="From" InputLabelProps={{ shrink: true }} value={customFrom} onChange={event => setCustomFrom(event.target.value)} /><TextField type="date" label="Through" InputLabelProps={{ shrink: true }} value={customTo} onChange={event => setCustomTo(event.target.value)} /></>}
    </Stack><Typography color="text.secondary" sx={{ my: 3 }}>Operational time zone: {zone}<br />Inclusive start: {formatDate(bounds.fromUtc)} · Exclusive end: {formatDate(bounds.toUtc)}</Typography>
    <Typography variant="subtitle2" sx={{ mb: 1 }}>Filters</Typography><Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 3 }}><TextField label="Location" value={filters.location} onChange={event => updateFilter('location', event.target.value)} /><TextField label="Carrier" value={filters.carrier} onChange={event => updateFilter('carrier', event.target.value)} /><TextField label="Status" value={filters.status} onChange={event => updateFilter('status', event.target.value)} /><TextField label="Recipient" value={filters.recipient} onChange={event => updateFilter('recipient', event.target.value)} /></Stack>
    <Typography variant="subtitle2" sx={{ mb: 1 }}>Layout options</Typography><Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 3 }}><FormControl sx={{ minWidth: 260 }}><InputLabel>Visible columns</InputLabel><Select multiple label="Visible columns" value={columns} onChange={event => setColumns(event.target.value)} renderValue={selected => `${selected.length} columns`}>{reportColumns.map(([key, label]) => <MenuItem key={key} value={key}><Checkbox checked={columns.includes(key)} />{label}</MenuItem>)}</Select></FormControl><FormControl sx={{ minWidth: 170 }}><InputLabel>Group by</InputLabel><Select label="Group by" value={groupBy} onChange={event => setGroupBy(event.target.value)}><MenuItem value="location">Location</MenuItem><MenuItem value="recipient">Recipient</MenuItem><MenuItem value="none">No grouping</MenuItem></Select></FormControl><FormControl sx={{ minWidth: 180 }}><InputLabel>Sort</InputLabel><Select label="Sort" value={sortOrder} onChange={event => setSortOrder(event.target.value)}><MenuItem value="occurred-asc">Oldest first</MenuItem><MenuItem value="occurred-desc">Newest first</MenuItem></Select></FormControl><FormControl sx={{ minWidth: 170 }}><InputLabel>Summary totals</InputLabel><Select label="Summary totals" value={includeSummary} onChange={event => setIncludeSummary(event.target.value)}><MenuItem value="true">Include</MenuItem><MenuItem value="false">Omit</MenuItem></Select></FormControl></Stack>
    <Typography variant="h6">Preview · {preview.length} rows</Typography><TableContainer sx={{ maxHeight: 220, mb: 2 }}><Table size="small"><TableHead><TableRow><TableCell>Occurred</TableCell><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell></TableRow></TableHead><TableBody>{preview.slice(0, 10).map(event => <TableRow key={event.eventId}><TableCell>{formatDate(event.occurredUtc)}</TableCell><TableCell sx={{ fontFamily: 'monospace' }}>{event.trackingNumber}</TableCell><TableCell>{event.carrier}</TableCell><TableCell>{event.location}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
    {(() => { const options = { type, period: range.toLowerCase(), timeZone: zone, fromDate: customFrom, toDate: customTo, columns: columns.join('|'), groupBy, sortOrder, includeSummary, ...filters }; return <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><Button variant="contained" startIcon={<PrintRounded />} onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'false' })}>Create PDF / Print</Button><Button onClick={() => onCreate({ ...options, action: 'csv', saveCopy: 'false' })}>Export CSV</Button><Button onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'true' })}>Save copy to shared reports</Button></Stack> })()}</CardContent></Card></>
}

function AttentionWorkspace({ conflicts = [], errors = [], notices = [], packages = [], onResolve, onRetry }) {
  const items = [...notices.map(value => ['Synchronization, configuration, or timing attention', value]), ...conflicts.map(value => ['Package conflict', value]), ...errors.map(value => ['Malformed synchronized record', value])]
  const conflictPackage = value => packages.find(item => item.status === 'CONFLICT' && value.includes(item.trackingNumber))
  return <><PageHeader title="Attention" instruction="Review issues that may require operator or supervisor action." />{items.length ? <Stack spacing={1.5}>{items.map(([title, value], index) => { const target = title === 'Package conflict' ? conflictPackage(value) : null; const retry = value.includes('locally durable'); return <Alert key={index} severity="warning" action={target ? <Button onClick={() => onResolve(target)}>Resolve…</Button> : retry ? <Button onClick={onRetry}>Retry safely</Button> : null}><Typography fontWeight={700}>{title}</Typography>{value}</Alert> })}</Stack> : <Alert severity="success">No work currently requires attention.</Alert>}</>
}

function SettingsWorkspace({ state, onConfigure, onSaveScanner, onSaveShared, onRollbackShared }) {
  const [scanner, setScanner] = useState({ ...state.scannerSettings, deviceId: state.deviceId })
  const [shared, setShared] = useState(state.sharedSettings)
  const [reviewing, setReviewing] = useState(false)
  useEffect(() => setScanner({ ...state.scannerSettings, deviceId: state.deviceId }), [state.scannerSettings, state.deviceId])
  useEffect(() => { setShared(state.sharedSettings); setReviewing(false) }, [state.sharedSettings])
  const update = (key, value) => setScanner(current => ({ ...current, [key]: value }))
  return <><PageHeader title="Settings" instruction="Configure this workstation without changing routine scanning." /><Card><CardContent><Typography variant="h6">Workstation</Typography><Divider sx={{ my: 2 }} /><TextField label="Device ID" value={scanner.deviceId} onChange={event => update('deviceId', event.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''))} sx={{ mb: 2 }} /><Detail label="Synchronized folder" value={state.sharedRoot || 'Not configured'} /><Button variant="outlined" sx={{ mt: 2 }} onClick={onConfigure}>Change folder</Button><Divider sx={{ my: 3 }} /><Typography variant="h6">Scanner</Typography><Typography color="text.secondary" sx={{ mb: 2 }}>These settings apply only to this workstation. Automatic mode accepts a scanner-speed burst after the configured quiet interval.</Typography>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} flexWrap="wrap">
      <FormControl sx={{ minWidth: 180 }}><InputLabel>Completion mode</InputLabel><Select label="Completion mode" value={scanner.completionMode} onChange={event => update('completionMode', event.target.value)}><MenuItem value="automatic">Automatic</MenuItem><MenuItem value="terminator">Terminator</MenuItem><MenuItem value="manual">Manual</MenuItem></Select></FormControl>
      <FormControl sx={{ minWidth: 150 }}><InputLabel>Terminator</InputLabel><Select label="Terminator" value={scanner.terminator} onChange={event => update('terminator', event.target.value)}><MenuItem value="Enter">Enter / CR</MenuItem><MenuItem value="Tab">Tab</MenuItem></Select></FormControl>
      <TextField type="number" label="Idle delay (ms)" value={scanner.idleDelayMs} onChange={event => update('idleDelayMs', Number(event.target.value))} inputProps={{ min: 80, max: 2000 }} />
      <TextField type="number" label="Burst threshold (ms)" value={scanner.burstThresholdMs} onChange={event => update('burstThresholdMs', Number(event.target.value))} inputProps={{ min: 10, max: 500 }} />
      <TextField type="number" label="Minimum length" value={scanner.minimumLength} onChange={event => update('minimumLength', Number(event.target.value))} inputProps={{ min: 4, max: 100 }} />
      <FormControl sx={{ minWidth: 180 }}><InputLabel>Sound feedback</InputLabel><Select label="Sound feedback" value={String(scanner.soundEnabled)} onChange={event => update('soundEnabled', event.target.value)}><MenuItem value="false">Off</MenuItem><MenuItem value="true">On</MenuItem></Select></FormControl>
    </Stack><Button variant="contained" sx={{ mt: 2 }} onClick={() => onSaveScanner(scanner)}>Save scanner settings</Button>
    <ScannerCalibration onRecommendation={values => setScanner(current => ({ ...current, ...values }))} />
    <Divider sx={{ my: 3 }} /><Typography variant="h6">Shared operational settings</Typography><Alert severity="warning" sx={{ my: 2 }}>Applies to all workstations after synchronization.</Alert>{state.sharedSettingsError && <Alert severity="error" sx={{ mb: 2 }}>{state.sharedSettingsError}</Alert>}
    <Stack spacing={2}><TextField label="Locations (separate with |)" value={shared.locations || ''} onChange={event => { setReviewing(false); setShared(current => ({ ...current, locations: event.target.value })) }} /><TextField label="Operational time zone" value={shared.operationalTimeZone || ''} onChange={event => { setReviewing(false); setShared(current => ({ ...current, operationalTimeZone: event.target.value })) }} /><TextField type="number" label="Pending attention threshold (minutes)" value={shared.pendingAttentionMinutes || 5} onChange={event => { setReviewing(false); setShared(current => ({ ...current, pendingAttentionMinutes: event.target.value })) }} /><FormControl><InputLabel>Retain raw barcode in events</InputLabel><Select label="Retain raw barcode in events" value={shared.retainRawBarcode || 'false'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, retainRawBarcode: event.target.value })) }}><MenuItem value="false">No</MenuItem><MenuItem value="true">Yes</MenuItem></Select></FormControl></Stack>
    {reviewing && <Alert severity="info" sx={{ mt: 2 }}>Review: locations, operational time zone, pending threshold, and barcode-retention policy will replace the effective shared values. The prior valid version will be retained and an audit event will be written.</Alert>}
    <Stack direction="row" spacing={1} sx={{ mt: 2 }}><Button variant="contained" onClick={() => reviewing ? onSaveShared(shared) : setReviewing(true)}>{reviewing ? 'Confirm shared changes' : 'Review shared changes'}</Button><Button onClick={onRollbackShared}>Rollback prior version</Button></Stack>
  </CardContent></Card></>
}

function ScannerCalibration({ onRecommendation }) {
  const [samples, setSamples] = useState([])
  const [value, setValue] = useState('')
  const timing = useRef({ last: 0, maxGapMs: 0 })
  const record = terminator => {
    if (!value) return
    const next = [...samples, { terminator, maxGapMs: timing.current.maxGapMs }]
    setSamples(next)
    setValue('')
    timing.current = { last: 0, maxGapMs: 0 }
    if (next.length === 3) onRecommendation(recommendScannerSettings(next))
  }
  return <Box sx={{ mt: 4, p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <Typography variant="h6">Guided scanner test</Typography>
    <Typography color="text.secondary" sx={{ mb: 2 }}>Scan the same test barcode three times. Values are discarded after each sample; only timing and terminator information is retained.</Typography>
    <TextField fullWidth value={value} disabled={samples.length === 3} label={`Test scan ${Math.min(samples.length + 1, 3)} of 3`}
      onKeyDown={event => {
        if ((event.key === 'Enter' || event.key === 'Tab') && value) { event.preventDefault(); record(event.key === 'Tab' ? 'Tab' : 'Enter'); return }
        if (event.key.length === 1) {
          const now = performance.now()
          if (timing.current.last) timing.current.maxGapMs = Math.max(timing.current.maxGapMs, now - timing.current.last)
          timing.current.last = now
        }
      }}
      onChange={event => setValue(event.target.value)} />
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
      <Button size="small" disabled={!value || samples.length === 3} onClick={() => record('')}>Record suffixless scan</Button>
      <Typography variant="body2" color="text.secondary">{samples.length}/3 samples recorded{samples.length === 3 ? ' · Recommendation applied; review and save settings.' : ''}</Typography>
      {samples.length > 0 && <Button size="small" onClick={() => { setSamples([]); setValue('') }}>Restart</Button>}
    </Stack>
  </Box>
}

function Diagnostics({ state, onRebuild, onExport }) {
  return <><PageHeader title="Diagnostics" instruction="Technical workstation and shared-folder details for support." /><Card><CardContent><Stack spacing={2}><Detail label="Workstation" value={state.deviceId} /><Detail label="Windows account" value={state.actor} /><Detail label="Shared root" value={state.sharedRoot || 'Not configured'} /><Detail label="Shared events discovered" value={state.eventCount} /><Detail label="Session activity events" value={state.sessionEventCount} /><Detail label="Locally pending events" value={state.pendingCount} /><Detail label="Last rescan" value={formatDate(state.refreshedUtc)} /><Detail label="Malformed records" value={state.errors?.length || 0} /></Stack><Divider sx={{ my: 3 }} /><Typography variant="h6">Effective settings and source</Typography><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Setting</TableCell><TableCell>Effective value</TableCell><TableCell>Source</TableCell></TableRow></TableHead><TableBody>{Object.entries(state.sharedSettings || {}).map(([key, value]) => <TableRow key={key}><TableCell>{key}</TableCell><TableCell>{value}</TableCell><TableCell>Shared operational configuration</TableCell></TableRow>)}{Object.entries(state.scannerSettings || {}).map(([key, value]) => <TableRow key={`scanner-${key}`}><TableCell>scanner.{key}</TableCell><TableCell>{String(value)}</TableCell><TableCell>Workstation preference</TableCell></TableRow>)}</TableBody></Table></TableContainer><Stack direction="row" spacing={1} sx={{ mt: 3 }}><Button variant="outlined" onClick={onRebuild}>Rebuild local projection</Button><Button variant="outlined" onClick={onExport}>Export redacted diagnostics</Button></Stack></CardContent></Card></>
}

function EmptyState() {
  return <Box sx={{ p: 6, textAlign: 'center' }}><Inventory2Rounded sx={{ fontSize: 42, color: 'text.disabled' }} /><Typography color="text.secondary">No packages to display.</Typography></Box>
}

function ActionDialog({ dialog, state, locations, busy, onClose, onConfigure, onConfirm, onDuplicate, onAssign, onBulkAssign, onCorrect, onResolve, onVoid, onNavigate, onFinishWithout }) {
  const [value, setValue] = useState('')
  const [destructiveReview, setDestructiveReview] = useState(false)
  const [correction, setCorrection] = useState({ location: '', recipient: '' })
  useEffect(() => {
    setValue(dialog?.type === 'setup' ? state.sharedRoot || '' : '')
    setDestructiveReview(false)
    setCorrection({ location: dialog?.package?.location || '', recipient: dialog?.package?.recipient || '' })
  }, [dialog])
  if (!dialog) return null
  if (dialog.type === 'menu') return <Dialog open onClose={onClose} maxWidth="xs" fullWidth><DialogTitle>Commercial Tracking</DialogTitle><DialogContent><List><ListItemButton onClick={() => { onNavigate('diagnostics'); onClose() }}><ListItemIcon><TroubleshootRounded /></ListItemIcon><ListItemText primary="Diagnostics" /></ListItemButton><ListItemButton onClick={() => api.shutdown().finally(() => window.close())}><ListItemIcon><LogoutRounded /></ListItemIcon><ListItemText primary="Exit application" /></ListItemButton></List></DialogContent></Dialog>
  const data = {
    setup: ['Configure synchronized folder', 'Full folder path', 'Save workstation settings'],
    recipient: ['Assign recipient', 'Recipient name', 'Assign recipient'],
    bulkRecipient: [`Assign recipient to ${dialog.packages?.length || 0} packages`, 'Recipient name', 'Assign recipient'],
    void: ['Void package', 'Reason for voiding', 'Void package']
  }[dialog.type]
  if (dialog.type === 'ambiguous') return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>Check this package</DialogTitle><DialogContent><Typography>This barcode could represent more than one supported format. Confirm the proposed values.</Typography><Alert severity="warning" sx={{ mt: 2 }}>Tracking number: <b>{dialog.response.trackingNumber}</b><br />Carrier: {dialog.response.carrier}</Alert></DialogContent><DialogActions><Button onClick={onClose}>Cancel—do not save</Button><Button variant="contained" onClick={onConfirm}>Confirm and receive</Button></DialogActions></Dialog>
  if (dialog.type === 'duplicate') return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>Check this package</DialogTitle><DialogContent><Typography>This tracking number was already received at <b>{dialog.response.location}</b> on {formatDate(dialog.response.occurredUtc)}.</Typography><Typography sx={{ mt: 2 }}>Tracking number: <b>{dialog.response.trackingNumber}</b></Typography></DialogContent><DialogActions><Button onClick={() => onDuplicate('keep')}>Keep existing record</Button><Button variant="contained" onClick={() => onDuplicate('location')}>Record location change</Button></DialogActions></Dialog>
  if (dialog.type === 'finish') return <Dialog open maxWidth="sm" fullWidth><DialogTitle>Prepare an inbound manifest?</DialogTitle><DialogContent><Alert severity="warning">{dialog.count} packages in this session are not on an audited manifest.</Alert></DialogContent><DialogActions><Button onClick={onClose}>Keep session open</Button><Button color="warning" onClick={() => setValue('confirm-close')}>Close without manifest…</Button><Button variant="contained" onClick={() => { onNavigate('manifests'); onClose() }}>Prepare inbound manifest</Button></DialogActions>{value === 'confirm-close' && <DialogContent><Divider sx={{ mb: 2 }} /><Typography>Closing will retain all package events, but these packages will remain unmanifested.</Typography><Button color="error" variant="contained" sx={{ mt: 2 }} onClick={onFinishWithout}>Confirm close without manifest</Button></DialogContent>}</Dialog>
  if (dialog.type === 'correction') return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>Correct package</DialogTitle><DialogContent><Alert severity="info" sx={{ mb: 2 }}>The previous values remain in the audit timeline. Package identity and audit timestamps cannot be changed.</Alert><Stack spacing={2}><FormControl fullWidth><InputLabel>Location</InputLabel><Select label="Location" value={correction.location} onChange={event => setCorrection(current => ({ ...current, location: event.target.value }))}>{locations.map(item => <MenuItem key={item} value={item}>{item}</MenuItem>)}</Select></FormControl><TextField label="Recipient" value={correction.recipient} onChange={event => setCorrection(current => ({ ...current, recipient: event.target.value }))} /><TextField label="Correction reason" multiline minRows={2} value={value} onChange={event => setValue(event.target.value)} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!value.trim()} onClick={() => onCorrect({ ...correction, reason: value })}>Record correction</Button></DialogActions></Dialog>
  if (dialog.type === 'conflict') return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>Resolve package conflict</DialogTitle><DialogContent><Alert severity="warning" sx={{ mb: 2 }}>All competing events remain in history. Select the accepted current outcome and provide a supervisor reason.</Alert><TextField select fullWidth label="Accepted outcome" value={correction.location || 'PICKED_UP'} onChange={event => setCorrection(current => ({ ...current, location: event.target.value }))} sx={{ mb: 2 }}><MenuItem value="PICKED_UP">Released</MenuItem><MenuItem value="READY_FOR_PICKUP">Awaiting pickup</MenuItem><MenuItem value="VOIDED">Voided</MenuItem></TextField><TextField fullWidth multiline minRows={2} label="Resolution reason" value={value} onChange={event => setValue(event.target.value)} /></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!value.trim()} onClick={() => onResolve({ trackingNumber: dialog.package.trackingNumber, acceptedStatus: correction.location || 'PICKED_UP', reason: value })}>Record resolution</Button></DialogActions></Dialog>
  const action = () => dialog.type === 'setup' ? onConfigure(value) : dialog.type === 'recipient' ? onAssign(value) : dialog.type === 'bulkRecipient' ? onBulkAssign(value, dialog.packages) : destructiveReview ? onVoid(value) : setDestructiveReview(true)
  return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>{data[0]}</DialogTitle><DialogContent>
    {dialog.type === 'setup' && <Alert severity="warning" sx={{ mb: 2 }}>Changing this folder changes where this workstation reads and writes shared records. Existing data is not migrated. Select the synchronized CommercialTracking root.</Alert>}
    {dialog.type === 'void' && <Alert severity="error" sx={{ mb: 2 }}>{destructiveReview ? 'Review the reason and confirm. This package will no longer be eligible for release.' : 'The original history remains. This creates a separate audited void event.'}</Alert>}
    <TextField autoFocus fullWidth multiline={dialog.type === 'void'} minRows={dialog.type === 'void' ? 3 : 1} label={data[1]} value={value} onChange={event => setValue(event.target.value)} />
  </DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" color={dialog.type === 'void' ? 'error' : 'primary'} disabled={!value.trim() || busy} onClick={action}>{dialog.type === 'void' ? destructiveReview ? 'Confirm void package' : 'Review void' : data[2]}</Button></DialogActions></Dialog>
}

createRoot(document.getElementById('root')).render(<ThemeProvider theme={theme}><App /></ThemeProvider>)
