sub init()
    m.top.functionName = "loadCatalog"
end sub

sub loadCatalog()
    url = PpvHttpUrl(m.top.apiUrl)
    if url = ""
        failCatalog("Set a valid HTTP or HTTPS apiUrl in config.json.")
        return
    end if
    port = CreateObject("roMessagePort")
    request = CreateObject("roUrlTransfer")
    request.SetMessagePort(port)
    request.SetCertificatesFile("common:/certs/ca-bundle.crt")
    request.InitClientCertificates()
    request.AddHeader("Accept", "application/json")
    request.SetUrl(url)
    if not request.AsyncGetToString()
        failCatalog("Unable to start the catalog request. Press * to retry.")
        return
    end if
    message = Wait(20000, port)
    if Type(message) <> "roUrlEvent"
        request.AsyncCancel()
        failCatalog("The catalog request timed out. Press * to retry.")
        return
    end if
    code = message.GetResponseCode()
    if code < 200 or code >= 300
        failCatalog("Catalog request failed (" + code.ToStr() + "). Press * to retry.")
        return
    end if
    data = ParseJson(message.GetString())
    if Type(data) <> "roAssociativeArray"
        failCatalog("The catalog response is not a JSON object. Press * to retry.")
        return
    end if
    if Type(data.streams) <> "roArray"
        failCatalog("The catalog response has no streams array. Press * to retry.")
        return
    end if
    if data.DoesExist("success")
        if not PpvBoolean(data.success)
            failCatalog("The catalog service reported an error. Press * to retry.")
            return
        end if
    end if
    m.top.result = PpvMapCatalog(data)
end sub

sub failCatalog(message as string)
    result = CreateObject("roSGNode", "ContentNode")
    result.AddFields({ errorMessage: message })
    m.top.result = result
end sub
