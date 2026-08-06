import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert, AppBar, Autocomplete, Box, Button, Card, CardContent, Checkbox, Chip, CircularProgress, CssBaseline,
  Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControl,
  IconButton, InputAdornment, InputLabel, List, ListItemButton, ListItemIcon, ListItemText, MenuItem,
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
import MenuRounded from '@mui/icons-material/MenuRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import FolderSharedRounded from '@mui/icons-material/FolderSharedRounded'
import { inboundEligible, custodyEligible, groupByRecipient } from './manifestEligibility.js'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import { api } from './api'
import { theme } from './theme'
import { formatDate, configureTimeFormat } from './format'
import { parseLocations, serializeLocations, addLocation } from './locations'
import { ScanStatus } from './ScanStatus'
import { ScannerCapture, recommendScannerSettings } from './scannerCapture'

const DRAWER = 248
const locations = ['Main Receiving', 'Loading Dock', 'Mailroom', 'Warehouse']
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
  const [releaseRecipient, setReleaseRecipient] = useState({ name: '', department: '', contactInfo: '', notes: '' })
  const [dialog, setDialog] = useState(null)
  const [toast, setToast] = useState('')
  const scanRef = useRef(null)
  const setupPrompted = useRef(false)
  const identityPrompted = useRef(false)

  const refresh = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setBusy(true)
      const next = await api.state()
      configureTimeFormat(next?.sharedSettings?.timeFormat)
      setState(next)
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
    if (state?.configured && !state.identityRegistered && !identityPrompted.current && !dialog) {
      identityPrompted.current = true
      setDialog({ type: 'identity' })
    }
  }, [state?.configured, state?.identityRegistered, dialog])

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
      const outboundContact = mode === 'Outbound' ? releaseRecipient : { name: '', department: '', contactInfo: '', notes: '' }
      const response = await api.scan({ raw: scan, mode, location, recipient: outboundContact.name, department: outboundContact.department, contactInfo: outboundContact.contactInfo, notes: outboundContact.notes, recipientBatchRelease: String(mode === 'Outbound' && !!outboundContact.name), confirmed: String(confirmed), duplicateAction, observedRevision: releaseCandidate?.revision ?? -1 })
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
      const candidate = await api.lookup({ raw: scan, recipient: releaseRecipient.name })
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

  const assign = async entry => {
    const target = dialog?.package || selected
    try {
      await api.assignRecipient({ trackingNumber: target.trackingNumber, recipient: entry.name, ...entry })
      setDialog(null); setToast('Recipient assigned'); await refresh(true)
    } catch (error) { setToast(error.message) }
  }

  const bulkAssign = async (entry, targets) => {
    try {
      const response = await api.assignRecipients({ recipient: entry.name, trackingNumbers: targets.map(item => item.trackingNumber).join('|'), ...entry })
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
      {page === 'release' && <ScanWorkspace mode="Outbound" state={state} scannerSettings={state.scannerSettings} location={location} scan={scan} setScan={value => { updateScanInput(value); setReleaseCandidate(null) }} result={result} busy={busy} scanRef={scanRef} submit={() => submitScan('Outbound')} recent={packages.filter(item => item.status === 'PICKED_UP')} releaseRecipient={releaseRecipient} setReleaseRecipient={value => { setReleaseRecipient(value); setReleaseCandidate(null); setTimeout(() => scanRef.current?.focus(), 0) }} />}
      {page === 'session' && <SessionWorkspace rows={session} activity={state.sessionActivity || []} selected={selected} setSelected={item => setSelected(item ? { ...item, activity: activity.filter(event => event.trackingNumber === item.trackingNumber) } : null)} onFinish={() => finishSession(false)} />}
      {page === 'history' && <HistoryWorkspace rows={packages} activity={activity} selected={selected} setSelected={setSelected} />}
      {page === 'recipients' && <RecipientWorkspace rows={unassigned} entries={state.addressBook || []} onAssign={targets => setDialog({ type: 'bulkRecipient', packages: targets })} onEdit={entry => setDialog({ type: 'addressBook', entry })} />}
      {page === 'manifests' && <ManifestWorkspace manifests={state.manifests || []} session={session} packages={packages} location={location} onFinalize={async payload => { try { const response = await api.manifest(payload); setToast(`Manifest finalized: ${response.manifestId}`); await refresh(true) } catch (error) { setToast(error.message) } }} onReprint={async manifestId => { try { const response = await api.reprintManifest({ manifestId }); setToast(response.message) } catch (error) { setToast(error.message) } }} />}
      {page === 'reports' && <ReportsWorkspace events={activity} packages={packages} onCreate={async payload => { try { const response = await api.report(payload); setToast(`${response.count} rows exported to ${response.csvFile}`) } catch (error) { setToast(error.message) } }} />}
      {page === 'attention' && <AttentionWorkspace conflicts={state.conflicts} errors={state.errors} notices={[...(state.attention || []), ...(state.warnings || [])]} packages={packages} onResolve={item => setDialog({ type: 'conflict', package: item })} onRetry={async () => { const response = await api.retryPending(); setToast(response.message); await refresh(true) }} />}
      {page === 'settings' && <SettingsWorkspace state={state} onConfigure={() => setDialog({ type: 'setup' })} onRegisterIdentity={async displayName => { try { await api.registerIdentity({ displayName }); setToast('Windows account registration saved'); await refresh(true) } catch (error) { setToast(error.message) } }} onSaveScanner={async values => { try { await api.preferences(values); setToast('Scanner settings saved'); await refresh(true) } catch (error) { setToast(error.message) } }} onSaveShared={async values => { try { const response = await api.saveSharedSettings({ ...values, confirmed: 'true' }); setToast(response.message); await refresh(true) } catch (error) { setToast(error.message) } }} onRollbackShared={async () => { try { const response = await api.rollbackSharedSettings(); setToast(response.message); await refresh(true) } catch (error) { setToast(error.message) } }} />}
      {page === 'diagnostics' && <Diagnostics state={state} onRebuild={async () => { const response = await api.rebuildProjection(); setToast(response.message); await refresh(true) }} onExport={async () => { const response = await api.exportDiagnostics(); setToast(`${response.message} ${response.file}`) }} />}
    </Box>
    {selected && <DetailPanel item={selected} onClose={() => setSelected(null)} onAssign={() => setDialog({ type: 'recipient' })} onCorrect={() => setDialog({ type: 'correction', package: selected })} onVoid={() => setDialog({ type: 'void' })} />}
    <ActionDialog dialog={dialog} state={state} locations={availableLocations} busy={busy} onClose={() => setDialog(null)} onRegisterIdentity={async displayName => { try { await api.registerIdentity({ displayName }); setDialog(null); setToast('Windows account registered'); await refresh(true) } catch (error) { setToast(error.message) } }} onConfigure={configure} onConfirm={() => { setDialog(null); submitScan(dialog.mode, true) }} onDuplicate={action => { setDialog(null); submitScan('Inbound', false, action) }} onAssign={assign} onBulkAssign={bulkAssign} onSaveAddressBook={async entry => { try { await api.saveAddressBook(entry); setDialog(null); setToast('Address book entry saved'); await refresh(true) } catch (error) { setToast(error.message) } }} onCorrect={correctPackage} onResolve={resolveConflict} onVoid={voidPackage} onNavigate={choosePage} onFinishWithout={() => finishSession(true)} />
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

function ScanWorkspace({ mode, state, scannerSettings, locations, location, setLocation, scan, setScan, result, busy, scanRef, submit, recent, onAssign, candidate, onConfirmRelease, releaseRecipient, setReleaseRecipient }) {
  const inbound = mode === 'Inbound'
  const [focused, setFocused] = useState(false)
  const armed = focused && !busy && state.configured
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
    <PageHeader title={inbound ? 'Receive Packages' : 'Release Packages'} instruction={inbound ? 'Confirm the location, then scan each package.' : 'Set the recipient once, then scan each package to assign and release it.'} />
    {!state.configured && <Alert severity="warning" sx={{ mb: 2 }}>Complete workstation setup in Settings before scanning.</Alert>}
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ p: { xs: 2, md: 3 } }}>
        {inbound && <FormControl size="small" sx={{ minWidth: 240, mb: 2 }}><InputLabel id="location-label">Receiving location</InputLabel><Select labelId="location-label" label="Receiving location" value={location} onChange={event => setLocation(event.target.value)}>{locations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
        {!inbound && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'flex-start' }} sx={{ mb: 2 }}><Autocomplete freeSolo fullWidth options={state.addressBook || []} getOptionLabel={option => typeof option === 'string' ? option : option.name} value={releaseRecipient?.name ? releaseRecipient : null} inputValue={releaseRecipient?.name || ''} onChange={(_, selected) => setReleaseRecipient(selected ? { name: selected.name || '', department: selected.department || '', contactInfo: selected.contactInfo || '', notes: selected.notes || '' } : { name: '', department: '', contactInfo: '', notes: '' })} onInputChange={(_, name, reason) => reason !== 'reset' && setReleaseRecipient({ name, department: '', contactInfo: '', notes: '' })} renderInput={params => <TextField {...params} required label="Recipient or department" helperText="This selection remains active for each package in this release batch." />} /><Button sx={{ mt: { sm: .75 }, whiteSpace: 'nowrap' }} disabled={!releaseRecipient?.name} onClick={() => setReleaseRecipient({ name: '', department: '', contactInfo: '', notes: '' })}>Clear recipient</Button></Stack>}
        <Box component="form" onSubmit={event => { event.preventDefault(); submit() }}>
          <TextField inputRef={scanRef} fullWidth autoComplete="off" disabled={busy || !state.configured || (!inbound && !releaseRecipient?.name?.trim())} value={scan}
            onKeyDown={captureKey} onPaste={event => { pasted.current = capture.current.paste(performance.now(), event.clipboardData.getData('text')) }}
            onChange={event => { if (event.target.value.length <= scan.length) capture.current.edit(); setScan(event.target.value) }}
            onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
            label={inbound ? 'Scan package barcode' : 'Scan or enter tracking number'} placeholder="Scanner input appears here"
            inputProps={{ 'aria-describedby': 'scan-help', spellCheck: false, autoCapitalize: 'off' }}
            InputProps={{ startAdornment: <InputAdornment position="start"><QrCodeScannerRounded sx={{ color: armed ? 'primary.main' : 'text.disabled' }} /></InputAdornment>, sx: { fontFamily: 'var(--ct-mono)', fontSize: 20, minHeight: 64, letterSpacing: '.02em' } }} />
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mt: 1 }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              {armed && <Box aria-hidden sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: 'success.main', animation: 'ctPulse 1.6s ease-in-out infinite' }} />}
              <Typography id="scan-help" variant="caption" color="text.secondary">{armed ? 'Listening for scanner — or type a tracking number and press Enter.' : 'Scan a barcode, or type and press Enter. Focus returns here after each action.'}</Typography>
            </Stack>
            <Button type="submit" variant="contained" disabled={!scan.trim() || busy || (!inbound && !releaseRecipient?.name?.trim())}>Process</Button>
          </Stack>
        </Box>
      </CardContent>
    </Card>
    <ScanStatus result={result} location={location} />
    {!inbound && candidate && <Card sx={{ mb: 2, borderColor: candidate.canRelease ? 'primary.main' : 'warning.main' }}><CardContent>
      <Typography variant="overline" color="text.secondary">Package verification</Typography>
      <Typography variant="h5" className="ct-mono" sx={{ mb: 2 }}>{candidate.trackingNumber}</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4}><Detail label="Assign and release to" value={<Typography variant="h6">{candidate.recipient || 'Unassigned'}</Typography>} />{candidate.currentRecipient && candidate.currentRecipient !== candidate.recipient && <Detail label="Previously assigned" value={candidate.currentRecipient} />}<Detail label="Carrier" value={candidate.carrier} /><Detail label="Received location" value={candidate.location} /><Detail label="Status" value={<StatusChip status={candidate.status} />} /></Stack>
      <Button variant="contained" size="large" sx={{ mt: 3 }} disabled={!candidate.canRelease || busy} onClick={onConfirmRelease}>Confirm assignment & release</Button>
    </CardContent></Card>}
    <Card variant="outlined"><CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="h6">{inbound ? 'Current session' : 'Recently released'} · {recent.length} packages</Typography></Stack>
      <RecentRows rows={recent.slice(0, 5)} onAssign={onAssign} />
    </CardContent></Card>
  </Box>
}

function RecentRows({ rows, onAssign }) {
  if (!rows.length) return <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>Packages will appear here after they are recorded.</Typography>
  return <Stack divider={<Divider />} sx={{ mt: 1 }}>{rows.map((row, index) => <Stack key={`${row.trackingNumber}-${index}`} direction="row" alignItems="center" spacing={2} sx={{ py: 1.5 }}>
    <Typography variant="body2" color="text.secondary" sx={{ width: 76, flexShrink: 0 }}>{formatDate(row.lastEventUtc || row.occurredUtc, true)}</Typography>
    <Chip size="small" variant="outlined" label={row.carrier || 'Other'} sx={{ width: 76, flexShrink: 0 }} />
    <Typography className="ct-mono" sx={{ fontWeight: 700, flexGrow: 1, minWidth: 0, overflowWrap: 'anywhere' }}>{row.trackingNumber}</Typography>
    <Typography variant="body2" color={row.recipient ? 'text.primary' : 'text.secondary'}>{row.recipient || 'Unassigned'}</Typography>
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

function RecipientWorkspace({ rows, entries, onAssign, onEdit }) {
  const [chosen, setChosen] = useState([])
  const toggle = row => setChosen(current => current.some(item => item.trackingNumber === row.trackingNumber) ? current.filter(item => item.trackingNumber !== row.trackingNumber) : [...current, row])
  return <><PageHeader title="Recipients & address book" instruction="Assign waiting packages and maintain reusable department or recipient details." action={<Stack direction="row" spacing={1}><Button onClick={() => onEdit(null)}>New address book entry</Button><Button variant="contained" disabled={!chosen.length} onClick={() => onAssign(chosen)}>Review assignment ({chosen.length})</Button></Stack>} />
    <Card>{rows.length ? <TableContainer><Table><TableHead><TableRow><TableCell padding="checkbox"><Checkbox aria-label="Select all packages" checked={chosen.length === rows.length} indeterminate={chosen.length > 0 && chosen.length < rows.length} onChange={event => setChosen(event.target.checked ? rows : [])} /></TableCell><TableCell>Received</TableCell><TableCell>Tracking number</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell></TableRow></TableHead><TableBody>{rows.map(row => <TableRow key={row.trackingNumber} hover><TableCell padding="checkbox"><Checkbox aria-label={`Select ${row.trackingNumber}`} checked={chosen.some(item => item.trackingNumber === row.trackingNumber)} onChange={() => toggle(row)} /></TableCell><TableCell>{formatDate(row.lastEventUtc)}</TableCell><TableCell className="ct-mono" sx={{ fontWeight: 700 }}>{row.trackingNumber}</TableCell><TableCell>{row.carrier}</TableCell><TableCell>{row.location}</TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState />}</Card>
    <Typography variant="h5" sx={{ mt: 4, mb: 1.5 }}>Address book</Typography>
    <Card>{entries.length ? <TableContainer><Table><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Department</TableCell><TableCell>Contact info</TableCell><TableCell>Notes</TableCell><TableCell></TableCell></TableRow></TableHead><TableBody>{entries.map(entry => <TableRow key={entry.name}><TableCell sx={{ fontWeight: 600 }}>{entry.name}</TableCell><TableCell>{entry.department || '—'}</TableCell><TableCell>{entry.contactInfo || '—'}</TableCell><TableCell sx={{ maxWidth: 360, whiteSpace: 'pre-wrap' }}>{entry.notes || '—'}</TableCell><TableCell><Button size="small" onClick={() => onEdit(entry)}>Edit</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState message="No saved recipients or departments yet. Assigning a package will add one automatically." />}</Card></>
}

function PackageTable({ rows, selected, onSelect }) {
  if (!rows.length) return <EmptyState />
  return <TableContainer sx={{ maxHeight: 'calc(100vh - 220px)' }}><Table stickyHeader><TableHead><TableRow><TableCell>Received</TableCell><TableCell>Tracking number</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell><TableCell>Status</TableCell></TableRow></TableHead><TableBody>
    {rows.map((row, index) => <TableRow hover tabIndex={0} key={`${row.trackingNumber}-${index}`} selected={selected?.trackingNumber === row.trackingNumber} onClick={() => onSelect(row)} onKeyDown={event => event.key === 'Enter' && onSelect(row)} sx={{ cursor: 'pointer' }}>
      <TableCell>{formatDate(row.lastEventUtc || row.occurredUtc)}</TableCell><TableCell className="ct-mono" sx={{ fontWeight: 700 }}>{row.trackingNumber}</TableCell><TableCell>{row.carrier || 'Other'}</TableCell><TableCell>{row.location || '—'}</TableCell><TableCell>{row.recipient || 'Unassigned'}</TableCell><TableCell><StatusChip status={row.status} /></TableCell>
    </TableRow>)}
  </TableBody></Table></TableContainer>
}

function ActivityTable({ rows }) {
  const labels = { PACKAGE_RECEIVED: 'Received', RECIPIENT_ASSIGNED: 'Recipient assigned', PACKAGE_LOCATION_CHANGED: 'Location changed', PACKAGE_RELEASED: 'Released', PACKAGE_VOIDED: 'Voided' }
  if (!rows.length) return <EmptyState />
  return <TableContainer><Table><TableHead><TableRow><TableCell>Recorded</TableCell><TableCell>Activity</TableCell><TableCell>Tracking number</TableCell><TableCell>Operator</TableCell><TableCell>Workstation</TableCell></TableRow></TableHead><TableBody>
    {rows.map(row => <TableRow key={row.eventId}><TableCell title={row.occurredUtc}>{formatDate(row.occurredUtc)}</TableCell><TableCell>{labels[row.eventType] || row.eventType}</TableCell><TableCell className="ct-mono">{row.trackingNumber}</TableCell><TableCell>{row.actorDisplayName ? `${row.actorDisplayName} (${row.actor})` : row.actor}</TableCell><TableCell>{row.deviceId}</TableCell></TableRow>)}
  </TableBody></Table></TableContainer>
}

function StatusChip({ status }) {
  const label = (status || 'UNKNOWN').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase())
  const color = status === 'CONFLICT' || status === 'VOIDED' ? 'error' : status === 'READY_FOR_PICKUP' ? 'success' : 'default'
  return <Chip size="small" variant="outlined" color={color} label={label} />
}

function DetailPanel({ item, onClose, onAssign, onCorrect, onVoid }) {
  return <Drawer anchor="right" open onClose={onClose} sx={{ '& .MuiDrawer-paper': { width: { xs: '100%', sm: 430 }, p: 3 } }}>
    <Typography variant="overline" color="text.secondary">Package detail</Typography><Typography variant="h5" className="ct-mono" sx={{ overflowWrap: 'anywhere' }}>{item.trackingNumber}</Typography>
    <Divider sx={{ my: 2 }} /><Stack spacing={2}><Detail label="Carrier" value={item.carrier} /><Detail label="Recipient" value={item.recipient || 'Unassigned'} /><Detail label="Location" value={item.location} /><Detail label="Status" value={<StatusChip status={item.status} />} /><Detail label="Last activity" value={formatDate(item.lastEventUtc || item.occurredUtc)} /></Stack>
    {!!item.activity?.length && <Box sx={{ mt: 3 }}><Typography variant="h6">Activity timeline</Typography><Stack sx={{ mt: 1 }} divider={<Divider />}>{item.activity.map(event => <Box key={event.eventId} sx={{ py: 1.25 }}><Typography fontWeight={500}>{(event.eventType || '').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase())}</Typography><Typography variant="body2" color="text.secondary">{formatDate(event.occurredUtc)} · {event.actorDisplayName ? `${event.actorDisplayName} (${event.actor})` : event.actor} · {event.deviceId}</Typography><Typography variant="caption" color="text.secondary" title={event.eventId}>Revision observed: {event.observedRevision} · UTC: {event.occurredUtc}</Typography></Box>)}</Stack></Box>}
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

function localDateToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function ManifestWorkspace({ manifests, packages, location, onFinalize, onReprint }) {
  const [tab, setTab] = useState(0)
  const [type, setType] = useState('inbound')
  const [date, setDate] = useState(localDateToday)
  const [manifestLocation, setManifestLocation] = useState(location || '')
  const [custodyRecipient, setCustodyRecipient] = useState('')
  const locations = useMemo(() => [...new Set(packages.map(item => item.location).filter(Boolean))], [packages])
  const custodyRecipients = useMemo(
    () => [...new Set(custodyEligible(packages, { date, recipient: '' }).map(item => item.recipient).filter(Boolean))].sort(),
    [packages, date])
  const eligible = useMemo(
    () => type === 'inbound'
      ? inboundEligible(packages, { date, location: manifestLocation })
      : custodyEligible(packages, { date, recipient: custodyRecipient }),
    [type, packages, date, manifestLocation, custodyRecipient])
  const groups = useMemo(
    () => type === 'custody' ? groupByRecipient(eligible) : [{ recipient: '', items: eligible }],
    [type, eligible])
  const [chosen, setChosen] = useState([])
  useEffect(() => setChosen(eligible), [type, date, manifestLocation, custodyRecipient, packages.length])
  const toggle = item => setChosen(current => current.some(value => value.trackingNumber === item.trackingNumber)
    ? current.filter(value => value.trackingNumber !== item.trackingNumber)
    : [...current, item])
  const included = item => chosen.some(value => value.trackingNumber === item.trackingNumber)
  const proposeId = () => {
    const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
    const suffix = crypto.randomUUID().replaceAll('-', '').slice(0, 6).toUpperCase()
    return `MNF-${stamp}-${suffix}`
  }
  const [manifestId, setManifestId] = useState(proposeId)
  useEffect(() => setManifestId(proposeId()), [manifests.length])
  const finalize = () => onFinalize({
    type,
    date,
    location: type === 'inbound' ? manifestLocation : '',
    recipient: type === 'custody' ? custodyRecipient : '',
    manifestId,
    trackingNumbers: chosen.map(item => item.trackingNumber).join('|')
  })
  return <><PageHeader title="Manifests" instruction="Prepare audited package lists and review finalized manifests." />
    <Card><Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 2 }}><Tab label="Prepare manifest" /><Tab label={`Manifest register (${manifests.length})`} /></Tabs><Divider />
      {tab === 0 ? <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <FormControl sx={{ minWidth: 220 }}><InputLabel>Purpose</InputLabel><Select label="Purpose" value={type} onChange={event => setType(event.target.value)}><MenuItem value="inbound">Inbound receiving</MenuItem><MenuItem value="custody">Recipient custody</MenuItem></Select></FormControl>
          <TextField type="date" label="Received date" value={date} onChange={event => setDate(event.target.value)} InputLabelProps={{ shrink: true }} sx={{ minWidth: 200 }} />
          {type === 'inbound' && <FormControl sx={{ minWidth: 220 }}><InputLabel>Location</InputLabel><Select label="Location" value={manifestLocation} onChange={event => setManifestLocation(event.target.value)}><MenuItem value="">All locations</MenuItem>{locations.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
          {type === 'custody' && <FormControl sx={{ minWidth: 240 }}><InputLabel>Recipient</InputLabel><Select label="Recipient" value={custodyRecipient} onChange={event => setCustodyRecipient(event.target.value)}><MenuItem value="">All recipients</MenuItem>{custodyRecipients.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</Select></FormControl>}
        </Stack>
        <Typography color="text.secondary" sx={{ my: 2 }}>Review exact membership before finalization. Finalization writes immutable audit events even if printing is canceled.</Typography>
        <TableContainer sx={{ maxHeight: 360 }}><Table size="small" stickyHeader><TableHead><TableRow><TableCell padding="checkbox"></TableCell><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell><TableCell>Recipient</TableCell></TableRow></TableHead>
          <TableBody>{groups.map(group => <React.Fragment key={group.recipient || 'all'}>
            {type === 'custody' && <TableRow><TableCell colSpan={5} sx={{ bgcolor: 'action.hover', fontWeight: 700 }}>{group.recipient} · {group.items.length}</TableCell></TableRow>}
            {group.items.map(item => <TableRow key={item.trackingNumber}><TableCell padding="checkbox"><Checkbox checked={included(item)} onChange={() => toggle(item)} /></TableCell><TableCell className="ct-mono">{item.trackingNumber}</TableCell><TableCell>{item.carrier}</TableCell><TableCell>{item.location}</TableCell><TableCell>{item.recipient || 'Unassigned'}</TableCell></TableRow>)}
          </React.Fragment>)}</TableBody></Table></TableContainer>
        <Typography sx={{ my: 1 }}>{chosen.length} included · {Math.max(0, eligible.length - chosen.length)} excluded</Typography>
        <Typography variant="body2">Proposed manifest ID: <b>{manifestId}</b> · Prepared: {formatDate(new Date().toISOString())}</Typography>
        {chosen.length > 100 && <Alert severity="warning" sx={{ my: 2 }}>Audited manifests are limited to 100 packages. Split this selection.</Alert>}
        <Button variant="contained" startIcon={<PrintRounded />} disabled={!chosen.length || chosen.length > 100} onClick={finalize} sx={{ mt: 1 }}>Finalize and open print view</Button>
      </CardContent>
      : manifests.length ? <TableContainer><Table><TableHead><TableRow><TableCell>Manifest ID</TableCell><TableCell>Type</TableCell><TableCell>Location/recipient</TableCell><TableCell>Prepared</TableCell><TableCell>Packages</TableCell><TableCell>Checksum</TableCell><TableCell></TableCell></TableRow></TableHead><TableBody>{manifests.map(item => <TableRow key={item.manifestId}><TableCell className="ct-mono" sx={{ fontWeight: 700 }}>{item.manifestId}</TableCell><TableCell>{item.type}</TableCell><TableCell>{item.location}</TableCell><TableCell>{formatDate(item.preparedUtc)}</TableCell><TableCell>{item.count}</TableCell><TableCell sx={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }} title={item.checksum}>{item.checksum || 'Pending'}</TableCell><TableCell><Button size="small" onClick={() => onReprint(item.manifestId)}>Reprint</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer> : <EmptyState />}
    </Card></>
}

function ReportsWorkspace({ onCreate, events, packages }) {
  const [type, setType] = useState('Receiving Activity')
  const [range, setRange] = useState('Day')
  const today = new Date().toISOString().slice(0, 10)
  const [customFrom, setCustomFrom] = useState(today)
  const [customTo, setCustomTo] = useState(today)
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone
  const [bounds, setBounds] = useState(reportRange(range, customFrom, customTo))
  const [filters, setFilters] = useState({ location: '', carrier: '', status: '', recipient: '' })
  const reportColumns = [['time', 'Occurred time'], ['tracking', 'Tracking number'], ['carrier', 'Carrier'], ['recipient', 'Recipient'], ['location', 'Location'], ['status', 'Status'], ['manifest', 'Manifest ID'], ['actor', 'Operator (Windows account)'], ['device', 'Workstation']]
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
    <Typography variant="h6">Preview · {preview.length} rows</Typography><TableContainer sx={{ maxHeight: 220, mb: 2 }}><Table size="small"><TableHead><TableRow><TableCell>Occurred</TableCell><TableCell>Tracking</TableCell><TableCell>Carrier</TableCell><TableCell>Location</TableCell></TableRow></TableHead><TableBody>{preview.slice(0, 10).map(event => <TableRow key={event.eventId}><TableCell>{formatDate(event.occurredUtc)}</TableCell><TableCell className="ct-mono">{event.trackingNumber}</TableCell><TableCell>{event.carrier}</TableCell><TableCell>{event.location}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
    {(() => { const options = { type, period: range.toLowerCase(), timeZone: zone, fromDate: customFrom, toDate: customTo, columns: columns.join('|'), groupBy, sortOrder, includeSummary, ...filters }; return <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><Button variant="contained" startIcon={<DescriptionRounded />} onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'false' })}>Create DOCX</Button><Button onClick={() => onCreate({ ...options, action: 'csv', saveCopy: 'false' })}>Export CSV</Button><Button onClick={() => onCreate({ ...options, action: 'print', saveCopy: 'true' })}>Save copy to shared reports</Button></Stack> })()}</CardContent></Card></>
}

function AttentionWorkspace({ conflicts = [], errors = [], notices = [], packages = [], onResolve, onRetry }) {
  const items = [...notices.map(value => ['Synchronization, configuration, or timing attention', value]), ...conflicts.map(value => ['Package conflict', value]), ...errors.map(value => ['Malformed synchronized record', value])]
  const conflictPackage = value => packages.find(item => item.status === 'CONFLICT' && value.includes(item.trackingNumber))
  return <><PageHeader title="Attention" instruction="Review issues that may require operator or supervisor action." />{items.length ? <Stack spacing={1.5}>{items.map(([title, value], index) => { const target = title === 'Package conflict' ? conflictPackage(value) : null; const retry = value.includes('locally durable'); return <Alert key={index} severity="warning" action={target ? <Button onClick={() => onResolve(target)}>Resolve…</Button> : retry ? <Button onClick={onRetry}>Retry safely</Button> : null}><Typography fontWeight={700}>{title}</Typography>{value}</Alert> })}</Stack> : <Alert severity="success">No work currently requires attention.</Alert>}</>
}

function SettingsWorkspace({ state, onConfigure, onRegisterIdentity, onSaveScanner, onSaveShared, onRollbackShared }) {
  const [scanner, setScanner] = useState({ ...state.scannerSettings, deviceId: state.deviceId })
  const [shared, setShared] = useState(state.sharedSettings)
  const [displayName, setDisplayName] = useState(state.actorDisplayName || '')
  const [reviewing, setReviewing] = useState(false)
  const [locationDraft, setLocationDraft] = useState('')
  const [locationError, setLocationError] = useState('')
  useEffect(() => setScanner({ ...state.scannerSettings, deviceId: state.deviceId }), [state.scannerSettings, state.deviceId])
  useEffect(() => { setShared(state.sharedSettings); setReviewing(false) }, [state.sharedSettings])
  useEffect(() => setDisplayName(state.actorDisplayName || ''), [state.actorDisplayName])
  const update = (key, value) => setScanner(current => ({ ...current, [key]: value }))
  const locationList = parseLocations(shared.locations)
  const commitLocation = () => {
    const result = addLocation(locationList, locationDraft)
    if (!result.ok) { setLocationError(result.error); return }
    setReviewing(false)
    setShared(current => ({ ...current, locations: serializeLocations(result.list) }))
    setLocationDraft(''); setLocationError('')
  }
  const removeLocation = value => {
    setReviewing(false)
    setShared(current => ({ ...current, locations: serializeLocations(locationList.filter(item => item !== value)) }))
  }
  return <><PageHeader title="Settings" instruction="Configure this workstation without changing routine scanning." /><Card><CardContent><Typography variant="h6">Signed-in operator</Typography><Typography color="text.secondary" sx={{ mb: 2 }}>The Windows account is detected at every startup. Register a readable name that will be written alongside the account in new audit events.</Typography><Detail label="Windows account" value={state.actor} /><Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 2 }}><TextField fullWidth label="Display name" value={displayName} onChange={event => setDisplayName(event.target.value)} /><Button variant="contained" disabled={!displayName.trim()} onClick={() => onRegisterIdentity(displayName)}>Register name</Button></Stack><Divider sx={{ my: 3 }} /><Typography variant="h6">Workstation</Typography><Divider sx={{ my: 2 }} /><TextField label="Device ID" value={scanner.deviceId} onChange={event => update('deviceId', event.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''))} sx={{ mb: 2 }} /><Detail label="Synchronized folder" value={state.sharedRoot || 'Not configured'} /><Button variant="outlined" sx={{ mt: 2 }} onClick={onConfigure}>Change folder</Button><Divider sx={{ my: 3 }} /><Typography variant="h6">Scanner</Typography><Typography color="text.secondary" sx={{ mb: 2 }}>These settings apply only to this workstation. Automatic mode accepts a scanner-speed burst after the configured quiet interval, and waits for multi-part 2D labels (FedEx and GS1) to finish before submitting.</Typography>
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
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Receiving locations</Typography>
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
          {locationList.length ? locationList.map(value => <Chip key={value} label={value} onDelete={() => removeLocation(value)} />) : <Typography variant="body2" color="text.secondary">Add at least one receiving location.</Typography>}
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <TextField fullWidth label="Add location" value={locationDraft} error={!!locationError} helperText={locationError || 'Press Enter or Add to append a location.'} onChange={event => { setLocationDraft(event.target.value); setLocationError('') }} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); commitLocation() } }} />
          <Button variant="outlined" onClick={commitLocation}>Add</Button>
        </Stack>
      </Box>
      <FormControl sx={{ maxWidth: 240 }}><InputLabel>Time display</InputLabel><Select label="Time display" value={shared.timeFormat || '12h'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, timeFormat: event.target.value })) }}><MenuItem value="12h">12-hour (1:30 PM)</MenuItem><MenuItem value="24h">24-hour (13:30)</MenuItem></Select></FormControl>
      <TextField type="number" label="Pending attention threshold (minutes)" value={shared.pendingAttentionMinutes || 5} onChange={event => { setReviewing(false); setShared(current => ({ ...current, pendingAttentionMinutes: event.target.value })) }} />
      <FormControl><InputLabel>Retain raw barcode in events</InputLabel><Select label="Retain raw barcode in events" value={shared.retainRawBarcode || 'false'} onChange={event => { setReviewing(false); setShared(current => ({ ...current, retainRawBarcode: event.target.value })) }}><MenuItem value="false">No</MenuItem><MenuItem value="true">Yes</MenuItem></Select></FormControl>
    </Stack>
    {reviewing && <Alert severity="info" sx={{ mt: 2 }}>Review: locations, time display, pending threshold, and barcode-retention policy will replace the effective shared values. The prior valid version will be retained and an audit event will be written.</Alert>}
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
  return <><PageHeader title="Diagnostics" instruction="Technical workstation and shared-folder details for support." /><Card><CardContent><Stack spacing={2}><Detail label="Workstation" value={state.deviceId} /><Detail label="Registered operator" value={state.actorDisplayName || 'Not registered'} /><Detail label="Windows account" value={state.actor} /><Detail label="Shared root" value={state.sharedRoot || 'Not configured'} /><Detail label="Shared events discovered" value={state.eventCount} /><Detail label="Session activity events" value={state.sessionEventCount} /><Detail label="Locally pending events" value={state.pendingCount} /><Detail label="Last rescan" value={formatDate(state.refreshedUtc)} /><Detail label="Malformed records" value={state.errors?.length || 0} /></Stack><Divider sx={{ my: 3 }} /><Typography variant="h6">Effective settings and source</Typography><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Setting</TableCell><TableCell>Effective value</TableCell><TableCell>Source</TableCell></TableRow></TableHead><TableBody>{Object.entries(state.sharedSettings || {}).map(([key, value]) => <TableRow key={key}><TableCell>{key}</TableCell><TableCell>{value}</TableCell><TableCell>Shared operational configuration</TableCell></TableRow>)}{Object.entries(state.scannerSettings || {}).map(([key, value]) => <TableRow key={`scanner-${key}`}><TableCell>scanner.{key}</TableCell><TableCell>{String(value)}</TableCell><TableCell>Workstation preference</TableCell></TableRow>)}</TableBody></Table></TableContainer><Stack direction="row" spacing={1} sx={{ mt: 3 }}><Button variant="outlined" onClick={onRebuild}>Rebuild local projection</Button><Button variant="outlined" onClick={onExport}>Export redacted diagnostics</Button></Stack></CardContent></Card></>
}

function EmptyState({ message = 'No packages to display.' }) {
  return <Box sx={{ px: 3, py: 7, textAlign: 'center' }}>
    <Box sx={{ width: 56, height: 56, mx: 'auto', mb: 1.5, borderRadius: '50%', display: 'grid', placeItems: 'center', bgcolor: 'action.hover', color: 'text.disabled' }}><Inventory2Rounded sx={{ fontSize: 30 }} /></Box>
    <Typography color="text.secondary">{message}</Typography>
  </Box>
}

function ActionDialog({ dialog, state, locations, busy, onClose, onRegisterIdentity, onConfigure, onConfirm, onDuplicate, onAssign, onBulkAssign, onSaveAddressBook, onCorrect, onResolve, onVoid, onNavigate, onFinishWithout }) {
  const [value, setValue] = useState('')
  const [destructiveReview, setDestructiveReview] = useState(false)
  const [correction, setCorrection] = useState({ location: '', recipient: '' })
  const [contact, setContact] = useState({ name: '', department: '', contactInfo: '', notes: '' })
  useEffect(() => {
    setValue(dialog?.type === 'setup' ? state.sharedRoot || '' : dialog?.type === 'identity' ? state.actorDisplayName || '' : '')
    setDestructiveReview(false)
    setCorrection({ location: dialog?.package?.location || '', recipient: dialog?.package?.recipient || '' })
    setContact(dialog?.entry ? { name: dialog.entry.name || '', department: dialog.entry.department || '', contactInfo: dialog.entry.contactInfo || '', notes: dialog.entry.notes || '' } : { name: '', department: '', contactInfo: '', notes: '' })
  }, [dialog])
  if (!dialog) return null
  if (dialog.type === 'menu') return <Dialog open onClose={onClose} maxWidth="xs" fullWidth><DialogTitle>Commercial Tracking</DialogTitle><DialogContent><List><ListItemButton onClick={() => { onNavigate('diagnostics'); onClose() }}><ListItemIcon><TroubleshootRounded /></ListItemIcon><ListItemText primary="Diagnostics" /></ListItemButton><ListItemButton onClick={() => api.shutdown().finally(() => window.close())}><ListItemIcon><LogoutRounded /></ListItemIcon><ListItemText primary="Exit application" /></ListItemButton></List></DialogContent></Dialog>
  if (dialog.type === 'identity') return <Dialog open maxWidth="sm" fullWidth><DialogTitle>Register signed-in operator</DialogTitle><DialogContent><Alert severity="info" sx={{ mb: 2 }}>Windows account <b>{state.actor}</b> was detected for this session. Enter the human-readable name that should accompany it in future audit records.</Alert><TextField autoFocus required fullWidth label="Your display name" value={value} onChange={event => setValue(event.target.value)} helperText="The Windows account remains the durable identity; this name makes reports and history readable." /></DialogContent><DialogActions><Button variant="contained" disabled={!value.trim() || busy} onClick={() => onRegisterIdentity(value)}>Register and continue</Button></DialogActions></Dialog>
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
  if (dialog.type === 'addressBook') return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>{dialog.entry ? 'Edit address book entry' : 'New address book entry'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}><TextField autoFocus required label="Recipient or department name" value={contact.name} onChange={event => setContact(current => ({ ...current, name: event.target.value }))} /><TextField label="Department" value={contact.department} onChange={event => setContact(current => ({ ...current, department: event.target.value }))} /><TextField label="Internal contact info" helperText="Extension, email, room, or other internal contact details" value={contact.contactInfo} onChange={event => setContact(current => ({ ...current, contactInfo: event.target.value }))} /><TextField multiline minRows={3} label="Notes" value={contact.notes} onChange={event => setContact(current => ({ ...current, notes: event.target.value }))} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!contact.name.trim() || busy} onClick={() => onSaveAddressBook(contact)}>Save entry</Button></DialogActions></Dialog>
  if (dialog.type === 'recipient' || dialog.type === 'bulkRecipient') {
    const choose = (_, selected) => setContact(selected ? { name: selected.name || '', department: selected.department || '', contactInfo: selected.contactInfo || '', notes: selected.notes || '' } : { name: '', department: '', contactInfo: '', notes: '' })
    return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>{dialog.type === 'bulkRecipient' ? `Assign recipient to ${dialog.packages?.length || 0} packages` : 'Assign recipient'}</DialogTitle><DialogContent><Alert severity="info" sx={{ mb: 2 }}>The recipient or department will be saved for future assignments. Optional contact details stay in the address book.</Alert><Stack spacing={2}><Autocomplete freeSolo options={state.addressBook || []} getOptionLabel={option => typeof option === 'string' ? option : option.name} value={contact.name ? contact : null} inputValue={contact.name} onChange={choose} onInputChange={(_, name, reason) => reason !== 'reset' && setContact(current => ({ ...current, name }))} renderInput={params => <TextField {...params} autoFocus required label="Recipient or department" />} /><TextField label="Department" value={contact.department} onChange={event => setContact(current => ({ ...current, department: event.target.value }))} /><TextField label="Internal contact info" value={contact.contactInfo} onChange={event => setContact(current => ({ ...current, contactInfo: event.target.value }))} /><TextField multiline minRows={2} label="Notes" value={contact.notes} onChange={event => setContact(current => ({ ...current, notes: event.target.value }))} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!contact.name.trim() || busy} onClick={() => dialog.type === 'recipient' ? onAssign(contact) : onBulkAssign(contact, dialog.packages)}>Assign recipient</Button></DialogActions></Dialog>
  }
  const action = () => dialog.type === 'setup' ? onConfigure(value) : destructiveReview ? onVoid(value) : setDestructiveReview(true)
  return <Dialog open onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>{data[0]}</DialogTitle><DialogContent>
    {dialog.type === 'setup' && (state.sharedRoot
      ? <Alert severity="warning" sx={{ mb: 2 }}>Changing this folder changes where this workstation reads and writes shared records. Existing data is not migrated. Select the synchronized CommercialTracking root.</Alert>
      : <Alert severity="info" icon={<FolderSharedRounded />} sx={{ mb: 2 }}>Point this workstation at the shared CommercialTracking folder your team keeps in sync (OneDrive, a network share, and the like). Every record is read from and written here.</Alert>)}
    {dialog.type === 'void' && <Alert severity="error" sx={{ mb: 2 }}>{destructiveReview ? 'Review the reason and confirm. This package will no longer be eligible for release.' : 'The original history remains. This creates a separate audited void event.'}</Alert>}
    <TextField autoFocus fullWidth multiline={dialog.type === 'void'} minRows={dialog.type === 'void' ? 3 : 1} label={data[1]} value={value} onChange={event => setValue(event.target.value)} />
  </DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" color={dialog.type === 'void' ? 'error' : 'primary'} disabled={!value.trim() || busy} onClick={action}>{dialog.type === 'void' ? destructiveReview ? 'Confirm void package' : 'Review void' : data[2]}</Button></DialogActions></Dialog>
}

createRoot(document.getElementById('root')).render(<ThemeProvider theme={theme}><App /></ThemeProvider>)
