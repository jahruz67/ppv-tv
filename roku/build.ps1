#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '../build/roku/ppvst-roku.zip')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$resourceRoot = Join-Path $repoRoot 'app/src/main/res'
$packageFiles = @(
    @{ Source = (Join-Path $PSScriptRoot 'manifest'); Entry = 'manifest' },
    @{ Source = (Join-Path $PSScriptRoot 'config.json'); Entry = 'config.json' },
    @{ Source = (Join-Path $resourceRoot 'mipmap-xxxhdpi/ic_launcher.png'); Entry = 'images/channel-icon.png' },
    @{ Source = (Join-Path $resourceRoot 'drawable/tv_banner.png'); Entry = 'images/splash.png' },
    @{ Source = (Join-Path $resourceRoot 'drawable/img_sports_fallback.jpg'); Entry = 'images/event-fallback.jpg' }
)

$sourceFolders = @((Join-Path $PSScriptRoot 'source'), (Join-Path $PSScriptRoot 'components'))
foreach ($sourceFile in Get-ChildItem -LiteralPath $sourceFolders -Recurse -File) {
    $packageFiles += @{
        Source = $sourceFile.FullName
        Entry = [IO.Path]::GetRelativePath($PSScriptRoot, $sourceFile.FullName).Replace('\', '/')
    }
}

foreach ($packageFile in $packageFiles) {
    if (-not (Test-Path -LiteralPath $packageFile.Source -PathType Leaf)) {
        throw "Required Roku package file is missing: $($packageFile.Source)"
    }
}
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'config.json') -Raw | ConvertFrom-Json
$apiUri = $null
if (-not [Uri]::TryCreate($config.apiUrl, [UriKind]::Absolute, [ref]$apiUri) -or
    $apiUri.Scheme -notin @('http', 'https')) {
    throw 'roku/config.json must contain an absolute HTTP or HTTPS apiUrl.'
}

$archivePath = [IO.Path]::GetFullPath($OutputPath)
if ([IO.Path]::GetExtension($archivePath) -ne '.zip') {
    throw 'The Roku package output path must end in .zip.'
}
foreach ($packageFile in $packageFiles) {
    if ([IO.Path]::GetFullPath($packageFile.Source) -eq $archivePath) {
        throw 'The output ZIP must not overwrite a source file.'
    }
}
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($archivePath)) | Out-Null
Add-Type -AssemblyName System.IO.Compression.ZipFile
$stream = [IO.File]::Open($archivePath, [IO.FileMode]::Create, [IO.FileAccess]::Write)
try {
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($packageFile in $packageFiles) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive, $packageFile.Source, $packageFile.Entry,
                [IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    $stream.Dispose()
}
Write-Output "Roku sideload package: $archivePath"
