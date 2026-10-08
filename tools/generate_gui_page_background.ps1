Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'resource-pack\assets\gradostroy\textures\font\gui\frame-overlay.png'
$width = 176
$height = 222
$random = [System.Random]::new(74108)
$bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($y = 0; $y -lt $height; $y++) {
    for ($x = 0; $x -lt $width; $x++) {
        $edge = [Math]::Min([Math]::Min($x, $width - 1 - $x), [Math]::Min($y, $height - 1 - $y))
        $noise = $random.Next(-5, 6)
        $shade = if ($edge -lt 5) { -17 + $noise } elseif ($edge -lt 11) { -7 + $noise } else { $noise }
        $bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
            [Math]::Clamp(235 + $shade, 0, 255),
            [Math]::Clamp(215 + $shade, 0, 255),
            [Math]::Clamp(170 + $shade, 0, 255)))
    }
}

$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

# Fine paper fibres and tiny warm stains keep the inventory's empty areas from looking flat.
for ($i = 0; $i -lt 115; $i++) {
    $x = $random.Next(12, $width - 12)
    $y = $random.Next(12, $height - 12)
    $w = $random.Next(1, 4)
    $h = $random.Next(1, 3)
    $alpha = $random.Next(12, 32)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($alpha, 133, 91, 44))
    $graphics.FillRectangle($brush, $x, $y, $w, $h)
    $brush.Dispose()
}

# Preserve the existing carved wood frame and gold corner fittings above the new paper.
$frame = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'assets/book-frame.png'))
$interior = [System.Drawing.Rectangle]::new(8, 14, 160, 198)
$graphics.ExcludeClip($interior)
$graphics.DrawImageUnscaled($frame, 0, 0)
$graphics.ResetClip()
$frame.Dispose()

# Minecraft 26.2 draws labels before items. The opaque page hides the chest grid,
# and both menu buttons and the player's actual inventory are rendered above it.
# Only the player inventory receives subtle parchment slot outlines.
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(150, 121, 77), 1)
$slotBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(31, 115, 76, 34))
for ($column = 0; $column -lt 9; $column++) {
    $x = 8 + (18 * $column)
    foreach ($y in @(139, 157, 175, 197)) {
        $graphics.FillRectangle($slotBrush, $x, $y, 16, 16)
        $graphics.DrawRectangle($pen, $x-1, $y-1, 17, 17)
    }
}
$graphics.DrawLine($pen, 12, 125, 163, 125)
$slotBrush.Dispose()
$pen.Dispose()
$graphics.Dispose()

# Keep the page lettering inside the paper. The source ornament has a wide inner
# bevel, which otherwise crosses the title and the vanilla inventory label.
$paper = [System.Drawing.Color]::FromArgb(235, 215, 170)
for ($y = 0; $y -lt 15; $y++) {
    for ($x = 12; $x -lt 164; $x++) { $bitmap.SetPixel($x, $y, $paper) }
}
for ($y = 14; $y -lt 212; $y++) {
    foreach ($x in 8..12) { $bitmap.SetPixel($x, $y, $paper) }
    foreach ($x in 163..167) { $bitmap.SetPixel($x, $y, $paper) }
}
# Restore the complete top ornament after clearing its inner bevel. The title
# now starts below this band, so the decorative edge remains intact.
$topFrame = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'assets/book-frame.png'))
for ($y = 0; $y -lt 14; $y++) {
    for ($x = 12; $x -lt 164; $x++) { $bitmap.SetPixel($x, $y, $topFrame.GetPixel($x, $y)) }
}
$topFrame.Dispose()

# Book cover header and the compact city summary panel. Dynamic server text is
# rendered above this bitmap by BookMenuTitle; these shapes provide the visual
# hierarchy from the reference book without baking demo data into the pack.
$decor = [System.Drawing.Graphics]::FromImage($bitmap)
$decor.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$decor.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit
$bannerDark = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(86, 19, 24))
$banner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(128, 28, 30))
$goldPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(234, 174, 59), 1)
$inkPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(117, 75, 38), 1)
$decor.FillRectangle($bannerDark, 27, 2, 122, 14)
$decor.FillRectangle($banner, 29, 3, 118, 11)
$decor.DrawRectangle($goldPen, 28, 2, 119, 13)
$decor.DrawLine($goldPen, 24, 9, 28, 9); $decor.DrawLine($goldPen, 148, 9, 152, 9)
$headerFont = [System.Drawing.Font]::new('Consolas', 8, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$headerBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 226, 188, 103))
$headerText = 'Книга управления'
$headerSize = $decor.MeasureString($headerText, $headerFont)
$decor.DrawString($headerText, $headerFont, $headerBrush, [System.Drawing.PointF]::new(88 - ($headerSize.Width / 2), 4))
$headerFont.Dispose()

$panelBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(38, 137, 91, 49))
$decor.FillRectangle($panelBrush, 10, 26, 158, 39)
$decor.DrawRectangle($inkPen, 10, 26, 157, 38)
$decor.DrawLine($goldPen, 90, 28, 90, 63)
# A small heraldic shield marks the city panel while the name and statistics
# remain live text supplied by the server.
$decor.FillPolygon($banner, [System.Drawing.Point[]]@([System.Drawing.Point]::new(20,30),[System.Drawing.Point]::new(34,30),[System.Drawing.Point]::new(34,44),[System.Drawing.Point]::new(27,50),[System.Drawing.Point]::new(20,44)))
$decor.DrawPolygon($goldPen, [System.Drawing.Point[]]@([System.Drawing.Point]::new(20,30),[System.Drawing.Point]::new(34,30),[System.Drawing.Point]::new(34,44),[System.Drawing.Point]::new(27,50),[System.Drawing.Point]::new(20,44)))
$decor.FillPolygon($headerBrush, [System.Drawing.Point[]]@([System.Drawing.Point]::new(22,38),[System.Drawing.Point]::new(23,35),[System.Drawing.Point]::new(25,37),[System.Drawing.Point]::new(27,33),[System.Drawing.Point]::new(29,37),[System.Drawing.Point]::new(31,35),[System.Drawing.Point]::new(32,38)))
$decor.FillRectangle($headerBrush, 23, 39, 9, 2)
$decor.FillRectangle($banner, 24, 35, 1, 1); $decor.FillRectangle($banner, 27, 33, 1, 1); $decor.FillRectangle($banner, 30, 35, 1, 1)
$infoFont = [System.Drawing.Font]::new('Arial', 6, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$infoBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 75, 43, 27))
foreach ($row in @(@('Королевство:', 27), @('Территория:', 36), @('Казна:', 45), @('Жителей:', 54))) {
    $decor.DrawString($row[0], $infoFont, $infoBrush, [System.Drawing.PointF]::new(49, $row[1]))
}
# Compact pixel icons for the four live values.
$decor.FillPolygon($goldPen.Brush, [System.Drawing.Point[]]@([System.Drawing.Point]::new(38,28),[System.Drawing.Point]::new(44,28),[System.Drawing.Point]::new(43,34),[System.Drawing.Point]::new(39,34)))
$decor.FillRectangle($headerBrush, 40, 30, 2, 3)
$decor.FillPolygon($banner, [System.Drawing.Point[]]@([System.Drawing.Point]::new(38,36),[System.Drawing.Point]::new(44,36),[System.Drawing.Point]::new(44,41),[System.Drawing.Point]::new(38,41)))
$decor.FillRectangle($headerBrush, 40, 37, 2, 3)
$decor.FillEllipse($headerBrush, 38, 46, 5, 4); $decor.FillEllipse($headerBrush, 42, 48, 5, 4)
$decor.FillEllipse($banner, 39, 55, 4, 4); $decor.FillRectangle($banner, 37, 59, 8, 2)
$infoFont.Dispose(); $infoBrush.Dispose()
$panelBrush.Dispose()

# Ornaments separate the menu from the vanilla inventory label.
$decor.DrawLine($inkPen, 11, 127, 72, 127); $decor.DrawLine($inkPen, 104, 127, 165, 127)
$decor.FillEllipse($headerBrush, 76, 125, 3, 3); $decor.FillEllipse($headerBrush, 97, 125, 3, 3)
$decor.Dispose(); $bannerDark.Dispose(); $banner.Dispose(); $goldPen.Dispose(); $inkPen.Dispose()

# Extend the carved wood to the true container edge. These bands deliberately
# stay outside the text-safe area (x=8..167, y=14..211).
$woodDark = [System.Drawing.Color]::FromArgb(78, 30, 24)
$wood = [System.Drawing.Color]::FromArgb(122, 48, 29)
$gold = [System.Drawing.Color]::FromArgb(231, 170, 55)
for ($y = 0; $y -lt $height; $y++) {
    $bitmap.SetPixel(0, $y, $woodDark); $bitmap.SetPixel(1, $y, $wood)
    $bitmap.SetPixel(2, $y, $gold); $bitmap.SetPixel(3, $y, $wood)
    $bitmap.SetPixel($width-1, $y, $woodDark); $bitmap.SetPixel($width-2, $y, $wood)
    $bitmap.SetPixel($width-3, $y, $gold); $bitmap.SetPixel($width-4, $y, $wood)
}
for ($x = 0; $x -lt $width; $x++) {
    $bitmap.SetPixel($x, 0, $woodDark); $bitmap.SetPixel($x, 1, $wood)
    $bitmap.SetPixel($x, 2, $gold); $bitmap.SetPixel($x, 3, $wood)
    $bitmap.SetPixel($x, $height-1, $woodDark); $bitmap.SetPixel($x, $height-2, $wood)
    $bitmap.SetPixel($x, $height-3, $gold); $bitmap.SetPixel($x, $height-4, $wood)
}

$bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)

# A wider visual-only cover. It is deliberately larger than the 176px
# container; the inventory and its hitboxes remain unchanged underneath it.
# Fill the added margins with leather so the wider frame joins the page instead
# of leaving a pale gap between two separate-looking borders.
$widePath = Join-Path (Split-Path $path) 'frame-overlay-wide.png'
$wide = [System.Drawing.Bitmap]::new(208, 254, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$wideGraphics = [System.Drawing.Graphics]::FromImage($wide)
$wideGraphics.Clear([System.Drawing.Color]::FromArgb(235, 215, 170))
$wideGraphics.DrawImageUnscaled($bitmap, 16, 16)
$wideGraphics.Dispose()
$leather = [System.Drawing.Color]::FromArgb(82, 31, 23)
$leatherLight = [System.Drawing.Color]::FromArgb(112, 43, 29)
for ($y = 0; $y -lt 254; $y++) {
    for ($x = 0; $x -lt 16; $x++) { $wide.SetPixel($x, $y, $(if ($x % 5 -eq 0) { $leatherLight } else { $leather })) }
    for ($x = 192; $x -lt 208; $x++) { $wide.SetPixel($x, $y, $(if ($x % 5 -eq 0) { $leatherLight } else { $leather })) }
}
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 208; $x++) { $wide.SetPixel($x, $y, $(if ($y % 5 -eq 0) { $leatherLight } else { $leather })) }
}
for ($y = 238; $y -lt 254; $y++) {
    for ($x = 0; $x -lt 208; $x++) { $wide.SetPixel($x, $y, $(if ($y % 5 -eq 0) { $leatherLight } else { $leather })) }
}
# A narrow royal ribbon sits within each new side band, clear of the menu slots.
for ($y = 34; $y -lt 220; $y++) {
    foreach ($x in @(4, 11, 196, 203)) { $wide.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(226, 164, 47)) }
    foreach ($x in @(5, 10, 197, 202)) { $wide.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(105, 20, 28)) }
    foreach ($x in @(6,7,8,9,198,199,200,201)) { $wide.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(132, 28, 32)) }
}
foreach ($ribbon in @(@(6,57), @(6,122), @(6,187), @(198,57), @(198,122), @(198,187))) {
    $x = $ribbon[0]; $y = $ribbon[1]
    foreach ($point in @(@(1,0),@(2,1),@(3,1),@(4,0),@(3,3),@(2,4),@(3,5),@(4,6),@(2,6),@(0,4),@(1,3),@(0,2))) {
        $wide.SetPixel($x + $point[0], $y + $point[1], [System.Drawing.Color]::FromArgb(255, 255, 213, 111))
    }
}
$edgeDark = [System.Drawing.Color]::FromArgb(62, 24, 20)
$edgeWood = [System.Drawing.Color]::FromArgb(111, 42, 28)
$edgeGold = [System.Drawing.Color]::FromArgb(231, 170, 55)
for ($y = 0; $y -lt 254; $y++) {
    $wide.SetPixel(0, $y, $edgeDark); $wide.SetPixel(1, $y, $edgeWood); $wide.SetPixel(2, $y, $edgeGold); $wide.SetPixel(3, $y, $edgeWood)
    $wide.SetPixel(207, $y, $edgeDark); $wide.SetPixel(206, $y, $edgeWood); $wide.SetPixel(205, $y, $edgeGold); $wide.SetPixel(204, $y, $edgeWood)
}
for ($x = 0; $x -lt 208; $x++) {
    $wide.SetPixel($x, 0, $edgeDark); $wide.SetPixel($x, 1, $edgeWood); $wide.SetPixel($x, 2, $edgeGold); $wide.SetPixel($x, 3, $edgeWood)
    $wide.SetPixel($x, 253, $edgeDark); $wide.SetPixel($x, 252, $edgeWood); $wide.SetPixel($x, 251, $edgeGold); $wide.SetPixel($x, 250, $edgeWood)
}
$wide.Save($widePath, [System.Drawing.Imaging.ImageFormat]::Png)
$wide.Dispose()
$bitmap.Dispose()

"Generated parchment page backgrounds: $path and $widePath"
