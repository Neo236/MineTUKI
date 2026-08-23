Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::Transparent)

$ear = [System.Drawing.Color]::FromArgb(164, 112, 81)
$orange = [System.Drawing.Color]::FromArgb(227, 122, 47)
$white = [System.Drawing.Color]::White
$black = [System.Drawing.Color]::Black

$pixels = @(
    "T,E,T,T,T,T,E,T",
    "O,O,O,O,O,O,O,O",
    "O,O,O,O,O,O,O,O",
    "O,O,O,O,O,O,O,O",
    "O,O,O,O,O,O,O,O",
    "B,W,O,O,O,O,W,B",
    "O,W,W,B,B,W,W,O",
    "O,W,W,W,W,W,W,O"
)

for ($y = 0; $y -lt 8; $y++) {
    $row = $pixels[$y].Split(',')
    for ($x = 0; $x -lt 8; $x++) {
        $color = [System.Drawing.Color]::Transparent
        switch ($row[$x]) {
            "E" { $color = $ear }
            "O" { $color = $orange }
            "W" { $color = $white }
            "B" { $color = $black }
        }
        
        # Scale 2x
        $bmp.SetPixel($x*2, $y*2, $color)
        $bmp.SetPixel($x*2+1, $y*2, $color)
        $bmp.SetPixel($x*2, $y*2+1, $color)
        $bmp.SetPixel($x*2+1, $y*2+1, $color)
    }
}
$bmp.Save("src/main/resources/assets/minetuki_updater/textures/gui/fox.png", [System.Drawing.Imaging.ImageFormat]::Png)
