# ======================================================
# Script para generar una API Key segura aleatoria
# ======================================================

# Generar 64 caracteres hexadecimales aleatorios (256-bit entropy)
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$apiKey = ($bytes | ForEach-Object { '{0:x2}' -f $_ }) -join ''

Write-Host "`n*** API Key Generada: ***" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------------"
Write-Host "$apiKey" -ForegroundColor Green
Write-Host "----------------------------------------------------------------"
Write-Host "Copie esta clave y peguela en su archivo .env o configuracion de Docker de PRODUCCION." -ForegroundColor Yellow
Write-Host "IMPORTANTE: Guardela en un lugar seguro (gestor de contrasenas)." -ForegroundColor Red
Write-Host ""
