sub init()
    m.top.backgroundColor = "0x0B111EFF"
    m.top.backgroundUri = ""
    m.rows = m.top.FindNode("rows")
    m.status = m.top.FindNode("status")
    m.video = m.top.FindNode("video")
    m.video.SetCertificatesFile("common:/certs/ca-bundle.crt")
    m.timer = m.top.FindNode("refreshTimer")
    m.task = CreateObject("roSGNode", "CatalogTask")
    m.pending = false
    m.selectedEvent = invalid
    m.currentSource = invalid
    m.sourceChoices = []
    m.playbackTask = invalid
    m.playbackRequestId = 0
    m.bridgeUrl = ""
    m.resumeAfterSetup = false
    m.settings = CreateObject("roRegistrySection", "PpvPlayback")
    m.rows.ObserveField("rowItemSelected", "onEventSelected")
    m.video.ObserveField("state", "onVideoStateChanged")
    m.task.ObserveField("result", "onCatalogReady")
    m.timer.ObserveField("fire", "refreshCatalog")
    m.top.SetFocus(true)
    config = ParseJson(ReadAsciiFile("pkg:/config.json"))
    if Type(config) <> "roAssociativeArray"
        m.status.text = "Unable to read config.json. Repackage the channel with a valid apiUrl."
        return
    end if
    if GetInterface(config.apiUrl, "ifString") = invalid
        m.status.text = "Set apiUrl in config.json and repackage the channel."
        return
    end if
    m.task.apiUrl = config.apiUrl
    m.bridgeUrl = PpvBridgeUrl(config.bridgeUrl)
    if m.settings.Exists("bridgeUrl") then m.bridgeUrl = PpvBridgeUrl(m.settings.Read("bridgeUrl"))
    refreshCatalog()
    m.timer.control = "start"
end sub

sub refreshCatalog()
    if m.pending or m.video.visible or m.top.dialog <> invalid then return
    if m.task.apiUrl = ""
        m.status.text = "Set a valid apiUrl in config.json and repackage the channel."
        return
    end if
    m.pending = true
    m.status.text = "Refreshing events..."
    m.task.control = "RUN"
end sub

sub onCatalogReady()
    m.pending = false
    catalog = m.task.result
    if catalog = invalid then return
    if catalog.errorMessage <> ""
        m.status.text = catalog.errorMessage
        if m.rows.content <> invalid then m.status.text = "Showing previous schedule. " + catalog.errorMessage
        return
    end if
    ' Keep the current selection and playing source stable until the user returns.
    if m.video.visible or m.top.dialog <> invalid
        m.status.text = "Refresh deferred while an event is open. Press * to refresh when browsing."
        return
    end if
    focused = m.rows.rowItemFocused
    m.rows.content = catalog
    if catalog.GetChildCount() = 0
        m.status.text = "No events are currently listed. Press * to refresh."
        m.top.SetFocus(true)
        return
    end if
    if focused.Count() = 2
        rowIndex = focused[0]
        if rowIndex < 0 then rowIndex = 0
        if rowIndex >= catalog.GetChildCount() then rowIndex = catalog.GetChildCount() - 1
        itemIndex = focused[1]
        if itemIndex < 0 then itemIndex = 0
        if itemIndex >= catalog.GetChild(rowIndex).GetChildCount() then itemIndex = 0
        m.rows.jumpToRowItem = [rowIndex, itemIndex]
    end if
    m.status.text = catalog.eventCount.ToStr() + " events | " + catalog.playableCount.ToStr() + " with sources"
    if m.bridgeUrl = "" then m.status.text = m.status.text + " | Press * to set up the playback bridge"
    m.rows.SetFocus(true)
end sub

sub onEventSelected()
    selected = m.rows.rowItemSelected
    if selected.Count() <> 2 or m.rows.content = invalid then return
    m.selectedEvent = m.rows.content.GetChild(selected[0]).GetChild(selected[1])
    showSources()
end sub

sub showSources()
    m.sourceChoices = []
    buttons = []
    for each source in m.selectedEvent.GetChildren(-1, 0)
        m.sourceChoices.Push(source)
        buttons.Push(source.title)
    end for
    message = m.selectedEvent.description
    if buttons.Count() = 0
        message = message + Chr(10) + Chr(10) + "No sources are currently listed for this event. Refresh the schedule or select another event."
    end if
    buttons.Push("Back to events")
    showDialog(m.selectedEvent.title, message, buttons, "sources")
end sub

sub showDialog(title as string, message as string, buttons as object, mode as string)
    closeDialog()
    dialog = CreateObject("roSGNode", "Dialog")
    dialog.title = title
    dialog.message = message
    dialog.buttons = buttons
    dialog.ObserveField("buttonSelected", "onDialogButton")
    dialog.ObserveField("wasClosed", "onDialogClosed")
    m.dialogMode = mode
    m.top.dialog = dialog
end sub

sub closeDialog()
    if m.top.dialog = invalid then return
    m.top.dialog.UnobserveField("wasClosed")
    m.top.dialog.UnobserveField("buttonSelected")
    m.top.dialog.close = true
    m.top.dialog = invalid
end sub

sub onDialogClosed()
    cancelResolution()
    m.resumeAfterSetup = false
    m.top.dialog = invalid
    focusCatalog()
end sub

sub onDialogButton()
    choice = m.top.dialog.buttonSelected
    if choice < 0 then return
    mode = m.dialogMode
    enteredAddress = ""
    if mode = "bridge" then enteredAddress = m.top.dialog.text
    closeDialog()
    if mode = "sources" and choice < m.sourceChoices.Count()
        m.currentSource = m.sourceChoices[choice]
        playSource()
    else if mode = "error" and choice = 0
        playSource()
    else if mode = "error" and choice = 1
        showSources()
    else if mode = "error" and choice = 2
        m.resumeAfterSetup = true
        showBridgeSetup()
    else if mode = "options" and choice = 0
        refreshCatalog()
        focusCatalog()
    else if mode = "options" and choice = 1
        m.resumeAfterSetup = false
        showBridgeSetup()
    else if mode = "bridge" and choice = 0
        saveBridgeAddress(enteredAddress)
    else if mode = "bridgeError" and choice = 0
        showBridgeSetup()
    else
        cancelResolution()
        m.resumeAfterSetup = false
        focusCatalog()
    end if
end sub

sub playSource()
    if m.currentSource = invalid then return
    if m.currentSource.embedUrl <> ""
        if m.bridgeUrl = ""
            m.resumeAfterSetup = true
            showBridgeSetup()
        else
            resolveSource()
        end if
        return
    end if
    startVideo(m.currentSource.url, m.currentSource.streamFormat)
end sub

sub startVideo(url as string, streamFormat as string)
    content = CreateObject("roSGNode", "ContentNode")
    content.title = m.selectedEvent.title
    content.url = url
    content.streamFormat = streamFormat
    content.live = m.selectedEvent.live
    m.video.content = content
    m.rows.visible = false
    m.video.visible = true
    m.video.SetFocus(true)
    m.video.control = "play"
end sub

sub resolveSource()
    cancelResolution()
    m.playbackTask = CreateObject("roSGNode", "PlaybackTask")
    m.playbackTask.bridgeUrl = m.bridgeUrl
    m.playbackTask.iframe = m.currentSource.embedUrl
    m.playbackRequestId = m.playbackRequestId + 1
    m.playbackTask.requestId = m.playbackRequestId
    m.playbackTask.ObserveField("result", "onPlaybackResolved")
    showDialog("Opening stream", "The playback bridge is resolving this source. This can take up to 45 seconds.", ["Cancel"], "resolving")
    m.playbackTask.control = "RUN"
end sub

sub onPlaybackResolved(event as object)
    if m.playbackTask = invalid then return
    result = event.GetData()
    if result = invalid then return
    if result.requestId <> m.playbackTask.requestId then return
    m.playbackTask.UnobserveField("result")
    m.playbackTask = invalid
    closeDialog()
    if result.errorMessage <> ""
        showPlaybackError(result.errorMessage)
        return
    end if
    startVideo(result.url, result.streamFormat)
end sub

sub cancelResolution()
    if m.playbackTask = invalid then return
    m.playbackTask.UnobserveField("result")
    m.playbackTask.control = "STOP"
    m.playbackTask = invalid
end sub

sub showBridgeSetup()
    closeDialog()
    dialog = CreateObject("roSGNode", "StandardKeyboardDialog")
    dialog.title = "Playback bridge address"
    dialog.message = "Enter your PC's LAN address and port, e.g. http://192.168.1.50:3003"
    dialog.text = m.bridgeUrl
    dialog.buttons = ["Save", "Cancel"]
    dialog.ObserveField("buttonSelected", "onDialogButton")
    dialog.ObserveField("wasClosed", "onDialogClosed")
    m.dialogMode = "bridge"
    m.top.dialog = dialog
end sub

sub saveBridgeAddress(address as string)
    baseUrl = PpvBridgeUrl(address)
    if baseUrl = ""
        showDialog("Enter the computer's address", "Start the playback bridge on your PC, then enter its LAN address including http:// and port 3003. Example: http://192.168.1.50:3003", ["Enter address", "Back"], "bridgeError")
        return
    end if
    m.bridgeUrl = baseUrl
    saved = m.settings.Write("bridgeUrl", baseUrl)
    if saved then saved = m.settings.Flush()
    m.status.text = "Playback bridge: " + baseUrl
    if not saved then m.status.text = m.status.text + " (could not save; using for this session)"
    resumePlayback = m.resumeAfterSetup
    m.resumeAfterSetup = false
    if resumePlayback
        playSource()
    else
        focusCatalog()
    end if
end sub

sub showPlaybackError(message as string)
    showDialog("Unable to open this source", message, ["Retry", "Change source", "Bridge settings", "Back to events"], "error")
end sub

sub stopPlayback()
    m.video.visible = false
    m.video.control = "stop"
    m.rows.visible = true
    focusCatalog()
end sub

sub focusCatalog()
    if m.rows.content <> invalid
        if m.rows.content.GetChildCount() > 0
            m.rows.SetFocus(true)
            return
        end if
    end if
    m.top.SetFocus(true)
end sub

sub onVideoStateChanged()
    if not m.video.visible then return
    if m.video.state = "finished"
        stopPlayback()
    else if m.video.state = "error"
        detail = "Video playback failed (" + m.video.errorCode.ToStr() + "). " + m.video.errorMsg
        stopPlayback()
        showPlaybackError(detail + Chr(10) + "Retry resolves a fresh stream. Check that the bridge computer is still running, or choose another source.")
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back" and m.video.visible
        stopPlayback()
        return true
    end if
    if key = "options" and not m.video.visible
        if m.top.dialog <> invalid then return false
        showDialog("Options", "For embedded streams, start the playback bridge on your computer and enter its LAN address here.", ["Refresh events", "Playback bridge address", "Back"], "options")
        return true
    end if
    return false
end function
