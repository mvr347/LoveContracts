Add-Type -AssemblyName System.IO.Compression.FileSystem

$baseDir = "C:\Users\maxim\Documents\antigravity"
$guiZip = "$baseDir\LoveContracts\gui-gen-4.skill"
$mbpZip = "$baseDir\LoveContracts\minecraft-bukkit-pro.skill"

if (Test-Path $guiZip) { Remove-Item $guiZip -Force }
if (Test-Path $mbpZip) { Remove-Item $mbpZip -Force }

[System.IO.Compression.ZipFile]::CreateFromDirectory("$baseDir\LoveContracts\temp_gui_gen_4", $guiZip)
[System.IO.Compression.ZipFile]::CreateFromDirectory("$baseDir\LoveContracts\temp_mbp", $mbpZip)

Write-Host "Created primary skill archives"

$skillFiles = Get-ChildItem -Path $baseDir -Recurse -Filter "*.skill*"

foreach ($file in $skillFiles) {
    if ($file.Name -like "gui-gen-4*") {
        Copy-Item -Path $guiZip -Destination $file.FullName -Force
        Write-Host "Updated $($file.FullName)"
    }
    elseif ($file.Name -like "minecraft-bukkit-pro*") {
        Copy-Item -Path $mbpZip -Destination $file.FullName -Force
        Write-Host "Updated $($file.FullName)"
    }
}
