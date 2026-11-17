sub init()
    m.top.functionName = "resolvePlayback"
end sub

sub resolvePlayback()
    bridgeUrl = PpvBridgeUrl(m.top.bridgeUrl)
    iframe = PpvHttpUrl(m.top.iframe)
    if bridgeUrl = "" or iframe = ""
        failPlayback("A bridge address and an HTTP(S) embed URL are required.")
        return
    end if
    port = CreateObject("roMessagePort")
    request = CreateObject("roUrlTransfer")
    request.SetMessagePort(port)
    request.SetCertificatesFile("common:/certs/ca-bundle.crt")
    request.InitClientCertificates()
    request.AddHeader("Accept", "application/json")
    request.AddHeader("Content-Type", "application/json")
    request.SetUrl(bridgeUrl + "/api/embed")
    request.RetainBodyOnError(true)
    if not request.AsyncPostFromString(FormatJson({ iframe: iframe }))
        failPlayback("Unable to contact the playback bridge. Check its address and that the computer is on.")
        return
    end if
    message = Wait(45000, port)
    if Type(message) <> "roUrlEvent"
        request.AsyncCancel()
        failPlayback("The playback bridge timed out. Check that it is running and reachable from your Roku.")
        return
    end if
    code = message.GetResponseCode()
    data = ParseJson(message.GetString())
    if code < 200 or code >= 300
        detail = ""
        if Type(data) = "roAssociativeArray" then detail = Left(PpvText(data.error), 240)
        if detail = "" then detail = Left(message.GetFailureReason(), 240)
        failPlayback("Bridge request failed (" + code.ToStr() + "). " + detail)
        return
    end if
    if Type(data) <> "roAssociativeArray"
        failPlayback("The bridge returned an invalid response. Use the PPV playback bridge address.")
        return
    end if
    url = PpvHttpUrl(data.proxiedUrl)
    if not PpvBoolean(data.ok) or Left(LCase(url), Len(bridgeUrl) + 1) <> bridgeUrl + "/"
        failPlayback("The bridge did not return a relayed stream. " + Left(PpvText(data.error), 240))
        return
    end if
    result = CreateObject("roSGNode", "ContentNode")
    result.url = url
    result.streamFormat = "hls"
    result.AddFields({ errorMessage: "", requestId: m.top.requestId })
    m.top.result = result
end sub

sub failPlayback(message as string)
    result = CreateObject("roSGNode", "ContentNode")
    result.AddFields({ errorMessage: message, requestId: m.top.requestId })
    m.top.result = result
end sub
