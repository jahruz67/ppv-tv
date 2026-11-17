sub onContentChanged()
    item = m.top.itemContent
    if item = invalid then return
    m.top.FindNode("poster").uri = item.hdPosterUrl
    m.top.FindNode("title").text = item.title
    m.top.FindNode("status").text = item.statusText
end sub
