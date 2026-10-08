$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build/previews'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$page = [Drawing.Bitmap]::new((Join-Path $root 'resource-pack/assets/gradostroy/textures/font/gui/frame-overlay-wide.png'))
$atlas = [Drawing.Bitmap]::new((Join-Path $root 'resource-pack/assets/gradostroy/textures/font/book/text.png'))
$font = Get-Content (Join-Path $root 'resource-pack/assets/gradostroy/font/book/line_18.json') -Raw | ConvertFrom-Json
$characters = -join $font.providers[1].chars

function Glyph-Width([char]$character) {
    if ($character -eq ' ') { return 3 }
    $i = $characters.IndexOf($character)
    if ($i -lt 0) { throw "Unsupported preview character: $character" }
    $last = -1
    for ($x = 0; $x -lt 12; $x++) {
        for ($y = 0; $y -lt 12; $y++) {
            if ($atlas.GetPixel(($i % 16) * 12 + $x, [Math]::Floor($i / 16) * 12 + $y).A -gt 0) { $last = $x }
        }
    }
    return [int][Math]::Floor(0.5 + (($last + 1) * 8 / 12.0)) + 1
}

function Draw-Text([string]$text, [int]$screenX, [int]$screenY, [bool]$center = $false, [System.Drawing.Color]$ink = [Drawing.Color]::FromArgb(255, 75, 43, 27)) {
    $width = 0
    foreach ($character in $text.ToCharArray()) { $width += Glyph-Width $character }
    if ($width -gt 160) { throw "Preview line overflows page: $text ($width px)" }
    $x = if ($center) { [int][Math]::Floor((176 - $width) / 2) } else { $screenX }
    # The cover is centered 16px outside the 176px inventory. Font line origins
    # use container coordinates, so add the same 16px offset to the preview.
    $x += 16
    $y = $screenY + 16
    foreach ($character in $text.ToCharArray()) {
        if ($character -ne ' ') {
            $i = $characters.IndexOf($character)
            for ($gx = 0; $gx -lt 12; $gx++) {
                for ($gy = 0; $gy -lt 12; $gy++) {
                    $pixel = $atlas.GetPixel(($i % 16) * 12 + $gx, [Math]::Floor($i / 16) * 12 + $gy)
                    if ($pixel.A -gt 0) {
                        $px = [int][Math]::Floor($gx * 8 / 12.0)
                        $py = [int][Math]::Floor($gy * 8 / 12.0)
                        $page.SetPixel($x + $px, $y + $py, $ink)
                    }
                }
            }
        }
        $x += Glyph-Width $character
    }
}

# Sample values mirror BookMenuTitle; live servers supply these from CitySnapshot.
Draw-Text 'Главное меню' 0 18 $true
Draw-Text 'Верхние Закупки' 95 27 $false ([Drawing.Color]::FromArgb(255, 113, 26, 22))
Draw-Text '6 / 64 чанков' 95 36 $false ([Drawing.Color]::FromArgb(255, 40, 22, 13))
Draw-Text '116 560' 95 45 $false ([Drawing.Color]::FromArgb(255, 40, 22, 13))
Draw-Text '1' 95 54 $false ([Drawing.Color]::FromArgb(255, 40, 22, 13))
Draw-Text 'Инвентарь' 8 130

$g = [Drawing.Graphics]::FromImage($page)
$g.CompositingMode = 'SourceOver'
$g.InterpolationMode = 'NearestNeighbor'
$g.PixelOffsetMode = 'Half'
$buttons = @{20='territory';24='resident';29='treasury';33='management';38='diplomacy';42='law';47='market';51='admin'}
foreach ($slot in $buttons.Keys) {
    $sprite = [Drawing.Bitmap]::new((Join-Path $root ("resource-pack/assets/gradostroy/textures/item/gui/button/menu_" + $buttons[$slot] + '.png')))
    $x = 16 + 8 + 18 * ($slot % 9) - 26
    $y = 16 + 17 + 18 * [Math]::Floor($slot / 9)
    $destination = [Drawing.Rectangle]::new($x, $y, 68, 18)
    $g.DrawImage($sprite, $destination, 2, 23, 68, 18, [Drawing.GraphicsUnit]::Pixel)
    $sprite.Dispose()
}
$g.Dispose()

$preview = [Drawing.Bitmap]::new($page.Width * 4, $page.Height * 4)
$g = [Drawing.Graphics]::FromImage($preview)
$g.InterpolationMode = 'NearestNeighbor'
$g.PixelOffsetMode = 'Half'
$g.DrawImage($page, [Drawing.Rectangle]::new(0, 0, $preview.Width, $preview.Height))
$path = Join-Path $output 'book-main.png'
$preview.Save($path, [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $preview.Dispose(); $page.Dispose(); $atlas.Dispose()
$path
