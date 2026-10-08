Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$textureDir = Join-Path $root 'resource-pack\assets\gradostroy\textures\item\gui\button'
$modelDir = Join-Path $root 'resource-pack\assets\gradostroy\models\item\gui\button'
$itemDir = Join-Path $root 'resource-pack\assets\gradostroy\items\gui\button'
New-Item -ItemType Directory -Force -Path $textureDir, $modelDir, $itemDir | Out-Null

$icons = @('city','territory','upgrade','treasury','resident','management','diplomacy','market',
    'confirm','cancel','back','admin','progression','protection','tax','law','overview','locked','info')
$color = @{
    ink = [System.Drawing.Color]::FromArgb(45, 24, 18)
    wood = [System.Drawing.Color]::FromArgb(103, 43, 27)
    woodLight = [System.Drawing.Color]::FromArgb(154, 74, 39)
    gold = [System.Drawing.Color]::FromArgb(231, 170, 55)
    goldLight = [System.Drawing.Color]::FromArgb(255, 220, 119)
    parchment = [System.Drawing.Color]::FromArgb(238, 211, 151)
    parchmentShade = [System.Drawing.Color]::FromArgb(188, 143, 81)
    green = [System.Drawing.Color]::FromArgb(69, 157, 62)
    greenLight = [System.Drawing.Color]::FromArgb(153, 215, 77)
    blue = [System.Drawing.Color]::FromArgb(54, 116, 171)
    red = [System.Drawing.Color]::FromArgb(171, 48, 35)
    stone = [System.Drawing.Color]::FromArgb(130, 132, 123)
    darkStone = [System.Drawing.Color]::FromArgb(74, 77, 76)
    skin = [System.Drawing.Color]::FromArgb(225, 166, 113)
}

function Fill-Rect($g, $c, [int]$x, [int]$y, [int]$w, [int]$h) {
    $brush = [System.Drawing.SolidBrush]::new($c); $g.FillRectangle($brush, $x, $y, $w, $h); $brush.Dispose()
}
function Fill-Ellipse($g, $c, [int]$x, [int]$y, [int]$w, [int]$h) {
    $brush = [System.Drawing.SolidBrush]::new($c); $g.FillEllipse($brush, $x, $y, $w, $h); $brush.Dispose()
}
function Fill-Poly($g, $c, [System.Drawing.Point[]]$points) {
    $brush = [System.Drawing.SolidBrush]::new($c); $g.FillPolygon($brush, $points); $brush.Dispose()
}
function Draw-Line($g, $c, [int]$width, [int]$x1, [int]$y1, [int]$x2, [int]$y2) {
    $pen = [System.Drawing.Pen]::new($c, $width); $pen.StartCap = 'Square'; $pen.EndCap = 'Square'; $g.DrawLine($pen, $x1, $y1, $x2, $y2); $pen.Dispose()
}
function Draw-Coin($g, [int]$x, [int]$y, [int]$size) {
    Fill-Ellipse $g $color.ink ($x+1) ($y+2) $size ($size-2)
    Fill-Ellipse $g $color.parchmentShade $x $y $size ($size-2)
    Fill-Ellipse $g $color.gold $x ($y-1) $size ($size-2)
    Fill-Ellipse $g $color.goldLight ($x+2) ($y+1) ($size-4) ($size-6)
    Fill-Ellipse $g $color.gold ($x+4) ($y+3) ($size-8) ($size-10)
}

foreach ($name in $icons) {
    # Item sprites must stay square: generated item models normalize their texture to 16x16.
    $bitmap = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.Clear([System.Drawing.Color]::Transparent)

    $outer = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new(5,0),[System.Drawing.Point]::new(27,0),[System.Drawing.Point]::new(31,4),
        [System.Drawing.Point]::new(31,27),[System.Drawing.Point]::new(27,31),[System.Drawing.Point]::new(5,31),
        [System.Drawing.Point]::new(1,27),[System.Drawing.Point]::new(1,4))
    $inner = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new(6,3),[System.Drawing.Point]::new(26,3),[System.Drawing.Point]::new(28,5),
        [System.Drawing.Point]::new(28,26),[System.Drawing.Point]::new(26,28),[System.Drawing.Point]::new(6,28),
        [System.Drawing.Point]::new(4,26),[System.Drawing.Point]::new(4,5))
    Fill-Poly $g $color.ink $outer
    $border = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new(5,1),[System.Drawing.Point]::new(27,1),[System.Drawing.Point]::new(30,4),
        [System.Drawing.Point]::new(30,27),[System.Drawing.Point]::new(27,30),[System.Drawing.Point]::new(5,30),
        [System.Drawing.Point]::new(2,27),[System.Drawing.Point]::new(2,4))
    Fill-Poly $g $color.gold $border
    Fill-Poly $g $color.woodLight $inner
    Fill-Rect $g $color.parchment 6 6 21 20
    Fill-Rect $g $color.parchmentShade 6 24 21 2
    Fill-Rect $g $color.goldLight 6 6 21 1
    Fill-Rect $g $color.ink 1 4 2 2; Fill-Rect $g $color.ink 29 4 2 2
    Fill-Rect $g $color.ink 1 26 2 2; Fill-Rect $g $color.ink 29 26 2 2

    switch ($name) {
        'city' {
            Fill-Rect $g $color.stone 9 12 14 12; Fill-Rect $g $color.darkStone 11 10 4 14; Fill-Rect $g $color.darkStone 18 8 4 16
            Fill-Poly $g $color.red ([System.Drawing.Point[]]@([System.Drawing.Point]::new(10,10),[System.Drawing.Point]::new(16,10),[System.Drawing.Point]::new(13,6)))
            Fill-Poly $g $color.red ([System.Drawing.Point[]]@([System.Drawing.Point]::new(17,8),[System.Drawing.Point]::new(23,8),[System.Drawing.Point]::new(20,4)))
            Fill-Rect $g $color.gold 13 16 2 3; Fill-Rect $g $color.gold 19 13 2 3; Fill-Rect $g $color.ink 16 18 3 6
        }
        'territory' {
            Fill-Poly $g $color.green ([System.Drawing.Point[]]@([System.Drawing.Point]::new(8,12),[System.Drawing.Point]::new(16,8),[System.Drawing.Point]::new(24,12),[System.Drawing.Point]::new(16,16)))
            Fill-Poly $g $color.greenLight ([System.Drawing.Point[]]@([System.Drawing.Point]::new(10,12),[System.Drawing.Point]::new(16,9),[System.Drawing.Point]::new(21,12),[System.Drawing.Point]::new(16,14)))
            Fill-Poly $g $color.wood ([System.Drawing.Point[]]@([System.Drawing.Point]::new(8,13),[System.Drawing.Point]::new(16,17),[System.Drawing.Point]::new(16,25),[System.Drawing.Point]::new(8,21)))
            Fill-Poly $g $color.parchmentShade ([System.Drawing.Point[]]@([System.Drawing.Point]::new(16,17),[System.Drawing.Point]::new(24,13),[System.Drawing.Point]::new(24,21),[System.Drawing.Point]::new(16,25)))
            Draw-Line $g $color.goldLight 1 16 17 16 24; Draw-Line $g $color.goldLight 1 12 15 12 23; Draw-Line $g $color.goldLight 1 20 15 20 23
        }
        'upgrade' {
            Fill-Ellipse $g $color.stone 9 8 14 14; Fill-Ellipse $g $color.darkStone 12 11 8 8; Fill-Ellipse $g $color.gold 14 13 4 4
            foreach($a in @(@(14,5,4,4),@(14,21,4,5),@(5,13,5,4),@(22,13,5,4),@(8,7,4,4),@(20,7,4,4),@(8,20,4,4),@(20,20,4,4))){Fill-Rect $g $color.stone $a[0] $a[1] $a[2] $a[3]}
        }
        'treasury' { Draw-Coin $g 8 15 12; Draw-Coin $g 14 10 13; Draw-Coin $g 18 17 10 }
        'resident' {
            Fill-Ellipse $g $color.skin 7 9 7 7; Fill-Ellipse $g $color.skin 18 8 7 7; Fill-Ellipse $g $color.goldLight 13 6 7 7
            Fill-Rect $g $color.blue 6 17 9 7; Fill-Rect $g $color.red 17 17 10 7; Fill-Rect $g $color.green 12 15 9 9
            Fill-Rect $g $color.ink 9 12 1 1; Fill-Rect $g $color.ink 20 11 1 1; Fill-Rect $g $color.ink 15 9 1 1
        }
        'management' { Fill-Rect $g $color.wood 9 6 15 20; Fill-Rect $g $color.parchment 11 7 12 17; Fill-Rect $g $color.gold 8 6 3 20; foreach($yy in @(11,15,19,23)){Fill-Rect $g $color.parchmentShade 13 $yy 8 1}; Fill-Rect $g $color.red 17 6 3 3 }
        'diplomacy' {
            Fill-Poly $g $color.green ([System.Drawing.Point[]]@([System.Drawing.Point]::new(7,7),[System.Drawing.Point]::new(17,9),[System.Drawing.Point]::new(17,22),[System.Drawing.Point]::new(12,26),[System.Drawing.Point]::new(7,22)))
            Fill-Poly $g $color.blue ([System.Drawing.Point[]]@([System.Drawing.Point]::new(17,9),[System.Drawing.Point]::new(26,6),[System.Drawing.Point]::new(26,21),[System.Drawing.Point]::new(21,25),[System.Drawing.Point]::new(17,22)))
            Fill-Rect $g $color.goldLight 11 11 2 8; Fill-Rect $g $color.goldLight 20 12 2 7; Fill-Rect $g $color.goldLight 9 14 6 2; Fill-Rect $g $color.goldLight 18 16 6 2
        }
        'market' {
            Fill-Rect $g $color.wood 8 14 17 11; Fill-Rect $g $color.parchment 10 16 13 8; Fill-Rect $g $color.wood 7 12 19 3
            foreach($xx in @(8,13,18,23)){Fill-Rect $g $color.red $xx 8 4 5}; Fill-Rect $g $color.goldLight 8 8 4 2; Fill-Rect $g $color.goldLight 18 8 4 2
            Draw-Coin $g 19 17 7
        }
        'confirm' { Fill-Ellipse $g $color.green 6 6 20 20; Fill-Ellipse $g $color.greenLight 8 7 16 16; Draw-Line $g $color.ink 5 10 16 14 20; Draw-Line $g $color.ink 5 14 20 23 10; Draw-Line $g $color.parchment 3 10 15 14 19; Draw-Line $g $color.parchment 3 14 19 22 10 }
        'cancel' { Fill-Ellipse $g $color.red 6 6 20 20; Fill-Ellipse $g $color.woodLight 8 7 16 16; Draw-Line $g $color.ink 5 11 11 21 21; Draw-Line $g $color.ink 5 21 11 11 21; Draw-Line $g $color.parchment 2 11 11 21 21; Draw-Line $g $color.parchment 2 21 11 11 21 }
        'back' { Fill-Poly $g $color.ink ([System.Drawing.Point[]]@([System.Drawing.Point]::new(7,16),[System.Drawing.Point]::new(16,7),[System.Drawing.Point]::new(16,12),[System.Drawing.Point]::new(25,12),[System.Drawing.Point]::new(25,20),[System.Drawing.Point]::new(16,20),[System.Drawing.Point]::new(16,25))) ; Fill-Poly $g $color.gold ([System.Drawing.Point[]]@([System.Drawing.Point]::new(8,16),[System.Drawing.Point]::new(16,9),[System.Drawing.Point]::new(16,13),[System.Drawing.Point]::new(24,13),[System.Drawing.Point]::new(24,19),[System.Drawing.Point]::new(16,19),[System.Drawing.Point]::new(16,23))) }
        'admin' { Fill-Ellipse $g $color.gold 8 7 10 10; Fill-Ellipse $g $color.parchment 11 10 4 4; Fill-Rect $g $color.gold 16 12 10 4; Fill-Rect $g $color.gold 22 15 3 5; Fill-Rect $g $color.gold 19 16 3 4; Fill-Ellipse $g $color.ink 12 11 2 2 }
        'progression' { Fill-Poly $g $color.gold ([System.Drawing.Point[]]@([System.Drawing.Point]::new(7,11),[System.Drawing.Point]::new(11,15),[System.Drawing.Point]::new(14,10),[System.Drawing.Point]::new(17,15),[System.Drawing.Point]::new(22,9),[System.Drawing.Point]::new(21,22),[System.Drawing.Point]::new(9,22))) ; Fill-Rect $g $color.red 9 23 13 2; Fill-Rect $g $color.parchment 13 17 5 3 }
        'protection' { Fill-Poly $g $color.ink ([System.Drawing.Point[]]@([System.Drawing.Point]::new(16,5),[System.Drawing.Point]::new(25,8),[System.Drawing.Point]::new(24,18),[System.Drawing.Point]::new(16,26),[System.Drawing.Point]::new(8,18),[System.Drawing.Point]::new(7,8))) ; Fill-Poly $g $color.blue ([System.Drawing.Point[]]@([System.Drawing.Point]::new(16,7),[System.Drawing.Point]::new(23,9),[System.Drawing.Point]::new(22,17),[System.Drawing.Point]::new(16,23),[System.Drawing.Point]::new(10,17),[System.Drawing.Point]::new(9,9))) ; Fill-Rect $g $color.goldLight 15 10 2 10; Fill-Rect $g $color.goldLight 11 14 10 2 }
        'tax' { Draw-Coin $g 8 14 11; Draw-Coin $g 15 9 12; Fill-Rect $g $color.wood 21 18 5 6; Fill-Rect $g $color.parchment 22 19 3 1; Fill-Rect $g $color.parchment 22 22 3 1 }
        'law' { Fill-Rect $g $color.parchmentShade 9 6 14 20; Fill-Rect $g $color.parchment 11 7 10 18; Fill-Rect $g $color.wood 9 7 3 3; Fill-Rect $g $color.wood 20 21 3 3; foreach($yy in @(12,16,20)){Fill-Rect $g $color.woodLight 13 $yy 6 1}; Fill-Rect $g $color.gold 14 23 5 1 }
        'overview' { Fill-Ellipse $g $color.ink 6 11 20 12; Fill-Ellipse $g $color.blue 8 12 16 9; Fill-Ellipse $g $color.parchment 12 12 8 8; Fill-Ellipse $g $color.red 14 14 4 4; Fill-Ellipse $g $color.ink 15 15 2 2; Fill-Rect $g $color.gold 15 6 2 4 }
        'locked' { Fill-Rect $g $color.wood 8 14 16 11; Fill-Rect $g $color.gold 10 16 12 8; Fill-Ellipse $g $color.ink 11 7 10 12; Fill-Ellipse $g $color.gold 13 9 6 8; Fill-Rect $g $color.ink 12 14 8 5; Fill-Rect $g $color.parchment 15 16 2 4 }
        default { Fill-Rect $g $color.wood 9 7 14 18; Fill-Rect $g $color.parchment 11 8 10 15; Fill-Rect $g $color.gold 15 10 2 2; Fill-Rect $g $color.woodLight 13 15 6 1; Fill-Rect $g $color.woodLight 13 19 6 1 }
    }

    $bitmap.Save((Join-Path $textureDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bitmap.Dispose()
    $modelJson = "{`"parent`":`"minecraft:item/generated`",`"textures`":{`"layer0`":`"gradostroy:item/gui/button/$name`"}}`n"
    $itemJson = "{`"model`":{`"type`":`"minecraft:model`",`"model`":`"gradostroy:item/gui/button/$name`"}}`n"
    [System.IO.File]::WriteAllText((Join-Path $modelDir "$name.json"), $modelJson, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $itemDir "$name.json"), $itemJson, [System.Text.UTF8Encoding]::new($false))
}

$menuButtons = [ordered]@{
    territory = 'Территория'
    resident = 'Жители'
    law = 'Законы'
    management = 'Управление'
    treasury = 'Казна'
    diplomacy = 'Дипломатия'
    market = 'Рынок'
    admin = 'Админ'
}

# Remove obsolete menu textures/models when the visual menu changes. This
# keeps the packaged resource pack deterministic instead of shipping stale
# buttons that are no longer reachable from the main book.
foreach ($obsolete in @('menu_upgrade')) {
    foreach ($dir in @($textureDir, $modelDir, $itemDir)) {
        $stale = Join-Path $dir ($obsolete + ($(if ($dir -eq $textureDir) { '.png' } else { '.json' })))
        if ([IO.File]::Exists($stale)) { [IO.File]::Delete($stale) }
    }
}

# A 72x72 transparent canvas with scale 4 maps the full 72px painting to a
# 64px GUI button. The extra source room is used for larger, crisp lettering.
$labelFont = [System.Drawing.Font]::new('Consolas', 8, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$labelFormat = [System.Drawing.StringFormat]::GenericTypographic.Clone()
$labelFormat.FormatFlags = $labelFormat.FormatFlags -bor [System.Drawing.StringFormatFlags]::NoWrap
$labelBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 241, 205))
$shadowBrush = [System.Drawing.SolidBrush]::new($color.ink)
foreach ($entry in $menuButtons.GetEnumerator()) {
    $name = 'menu_' + $entry.Key
    $bitmap = [System.Drawing.Bitmap]::new(72, 72, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    Fill-Rect $g $color.ink 0 22 72 20
    Fill-Rect $g $color.gold 1 23 70 18
    Fill-Rect $g $color.wood 2 24 68 16
    Fill-Rect $g $color.woodLight 3 25 66 1
    Fill-Rect $g $color.ink 3 39 66 1
    Fill-Rect $g $color.goldLight 1 23 70 1
    Fill-Rect $g $color.goldLight 1 24 1 15
    Fill-Rect $g $color.parchmentShade 70 25 1 15
    foreach ($corner in @(@(0,22), @(70,22), @(0,40), @(70,40))) {
        Fill-Rect $g $color.ink $corner[0] $corner[1] 2 2
        Fill-Rect $g $color.gold ($corner[0]+1) $corner[1] 1 1
    }

    $icon = [System.Drawing.Bitmap]::new((Join-Path $textureDir ($entry.Key + '.png')))
    $g.DrawImage($icon, [System.Drawing.Rectangle]::new(3, 24, 16, 16), 0, 0, 32, 32, [System.Drawing.GraphicsUnit]::Pixel)
    $icon.Dispose()
    $labelWidth = $g.MeasureString($entry.Value, $labelFont, 100, $labelFormat).Width
    if ($labelWidth -gt 51) { throw "Menu label '$($entry.Value)' exceeds the 51px label area: $labelWidth" }
    $g.DrawString($entry.Value, $labelFont, $shadowBrush, [System.Drawing.PointF]::new(21.5, 30), $labelFormat)
    $g.DrawString($entry.Value, $labelFont, $labelBrush, [System.Drawing.PointF]::new(21.5, 29), $labelFormat)

    $bitmap.Save((Join-Path $textureDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bitmap.Dispose()
    # A custom oversized quad is wider than the vanilla 16x16 item box. The
    # item definition opts into oversized GUI bounds, so Minecraft keeps the
    # extra width instead of normalising it back to a slot-sized square.
    $modelObject = [ordered]@{
        gui_light = 'front'
        textures = [ordered]@{ '0' = "gradostroy:item/gui/button/$name" }
        elements = @([ordered]@{
            from = @(-1, 0, 7); to = @(17, 16, 8)
            faces = [ordered]@{
                north = [ordered]@{ uv = @(0,0,16,16); texture = '#0' }
                south = [ordered]@{ uv = @(0,0,16,16); texture = '#0' }
            }
        })
        display = [ordered]@{ gui = [ordered]@{ rotation = @(0,0,0); translation = @(0,0,0); scale = @(4,4,1) } }
    }
    $modelJson = (($modelObject | ConvertTo-Json -Depth 8 -Compress) + "`n")
    $itemJson = "{`"oversized_in_gui`":true,`"model`":{`"type`":`"minecraft:model`",`"model`":`"gradostroy:item/gui/button/$name`"}}`n"
    [System.IO.File]::WriteAllText((Join-Path $modelDir "$name.json"), $modelJson, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $itemDir "$name.json"), $itemJson, [System.Text.UTF8Encoding]::new($false))
}
$labelBrush.Dispose(); $shadowBrush.Dispose(); $labelFont.Dispose(); $labelFormat.Dispose()

"Generated $($icons.Count) square 32x32 icons and $($menuButtons.Count) named 64x18 menu buttons on square 72x72 canvases."
