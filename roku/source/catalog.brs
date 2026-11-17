function PpvText(value as dynamic, fallback = "" as string) as string
    if GetInterface(value, "ifString") = invalid then return fallback
    value = value.Trim()
    if value = "" then return fallback
    return value
end function

function PpvNumber(value as dynamic) as double
    kind = Type(value)
    if kind = "Integer" or kind = "LongInteger" or kind = "Float" or kind = "Double" then return value
    if kind = "roInt" or kind = "roLongInteger" or kind = "roFloat" or kind = "roDouble" then return value
    if GetInterface(value, "ifString") <> invalid then return Val(value)
    return 0
end function

function PpvBoolean(value as dynamic) as boolean
    if Type(value) = "Boolean" or Type(value) = "roBoolean" then return value
    if LCase(PpvText(value)) = "true" then return true
    return PpvNumber(value) <> 0
end function

function PpvEpoch(value as dynamic) as double
    seconds = PpvNumber(value)
    if seconds > 10000000000# then seconds = Fix(seconds / 1000)
    if seconds <= 0 then return 0
    return seconds
end function

function PpvHttpUrl(value as dynamic) as string
    url = PpvText(value)
    pattern = CreateObject("roRegex", "^https?://[^/?#\s]+(?:[/?#].*)?$", "i")
    if not pattern.IsMatch(url) then return ""
    return url
end function

function PpvBridgeUrl(value as dynamic) as string
    url = LCase(PpvText(value))
    pattern = CreateObject("roRegex", "^https?://[^/?#\s@]+/?$", "i")
    if not pattern.IsMatch(url) then return ""
    if Right(url, 1) = "/" then url = Left(url, Len(url) - 1)
    return url
end function

function PpvStreamFormat(url as string, declaredFormat as dynamic) as string
    format = LCase(PpvText(declaredFormat))
    if format = "hls" or format = "dash" or format = "mp4" then return format
    path = LCase(url.Tokenize("?#")[0])
    if Right(path, 5) = ".m3u8" then return "hls"
    if Right(path, 4) = ".mpd" then return "dash"
    if Right(path, 4) = ".mp4" or Right(path, 4) = ".m4v" then return "mp4"
    return ""
end function

function PpvStatus(event as object, category as object, now as double) as string
    if PpvBoolean(event.always_live) or PpvBoolean(category.always_live) then return "24/7"
    if Instr(1, PpvText(event.tag), "24/7") > 0 then return "24/7"
    starts = PpvEpoch(event.starts_at)
    ends = PpvEpoch(event.ends_at)
    if starts = 0
        if ends > 0 and ends <= now then return "ENDED"
        return "UPCOMING"
    end if
    if starts > now then return "UPCOMING"
    if ends >= starts
        if now < ends then return "LIVE"
        return "ENDED"
    end if
    if now - starts <= 14400 then return "LIVE"
    return "ENDED"
end function

function PpvSchedule(event as object) as string
    starts = PpvEpoch(event.starts_at)
    if starts = 0 then return "Start time unavailable"
    date = CreateObject("roDateTime")
    date.FromSeconds(Int(starts))
    date.ToLocalTime()
    return date.AsDateString("short-date") + " " + date.AsTimeString("short-h24")
end function

sub PpvAddSource(content as object, source as object, seen as object, fallback as string)
    url = PpvHttpUrl(source.url)
    if url = "" then url = PpvHttpUrl(source.iframe)
    if url = "" then return
    if seen.DoesExist(url) then return
    format = PpvStreamFormat(url, source.stream_format)
    ' Keep embed sources: PlaybackTask resolves them through the companion bridge.
    if format = "" and url <> PpvHttpUrl(source.iframe) then return
    seen[url] = true
    item = content.CreateChild("ContentNode")
    item.title = PpvText(source.source_tag, PpvText(source.name, fallback))
    locale = PpvText(source.language, PpvText(source.locale))
    if locale <> "" then item.title = item.title + " | " + UCase(locale)
    item.url = url
    item.streamFormat = format
    item.AddFields({ embedUrl: "" })
    if format = ""
        item.embedUrl = url
        item.url = ""
        item.streamFormat = "hls"
    end if
end sub

function PpvMapEvent(event as object, category as object, now as double) as object
    item = CreateObject("roSGNode", "ContentNode")
    item.title = PpvText(event.name, "Untitled broadcast")
    item.hdPosterUrl = PpvHttpUrl(event.poster)
    status = PpvStatus(event, category, now)
    item.live = status = "LIVE" or status = "24/7"
    categoryName = PpvText(event.category_name, PpvText(category.category, "Sports"))
    item.description = status + " | " + categoryName + Chr(10) + PpvSchedule(event)
    item.AddFields({ statusText: status })
    seen = {}
    PpvAddSource(item, event, seen, "Primary stream")
    if Type(event.substreams) = "roArray"
        for each source in event.substreams
            if Type(source) = "roAssociativeArray" then PpvAddSource(item, source, seen, "Alternate stream")
        end for
    end if
    if item.GetChildCount() = 0 then item.statusText = status + " | No sources"
    return item
end function

function PpvMapCatalog(data as object) as object
    root = CreateObject("roSGNode", "ContentNode")
    root.AddFields({ errorMessage: "", eventCount: 0, playableCount: 0 })
    clock = CreateObject("roDateTime")
    now = clock.AsSeconds()
    for each category in data.streams
        if Type(category) = "roAssociativeArray"
            if Type(category.streams) = "roArray"
                row = CreateObject("roSGNode", "ContentNode")
                row.title = PpvText(category.category, "Sports")
                for each event in category.streams
                    if Type(event) = "roAssociativeArray"
                        item = PpvMapEvent(event, category, now)
                        row.AppendChild(item)
                        root.eventCount = root.eventCount + 1
                        if item.GetChildCount() > 0 then root.playableCount = root.playableCount + 1
                    end if
                end for
                if row.GetChildCount() > 0 then root.AppendChild(row)
            end if
        end if
    end for
    return root
end function
