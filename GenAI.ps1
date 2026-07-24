# 1. Read API Key from environment variable
$apiKey = $env:GENAI_API_KEY

# Check if the environment variable is set
if ([string]::IsNullOrEmpty($apiKey)) {
    Write-Host "Error: The GENAI_API_KEY environment variable is not set." -ForegroundColor Red
    Write-Host "Please set it before running the script: `$env:GENAI_API_KEY = 'your-key-here'" -ForegroundColor Yellow
    return
}

# 2. Define the API endpoint for listing models
$uri = "https://api.genai.mil/v1/models"

# 3. Construct the authorization header
$headers = @{
    "Authorization" = "Bearer $apiKey"
}

Write-Host "Requesting available models from $uri..."

# 4. Make the GET request using -SkipHttpErrorCheck
# -StatusCodeVariable captures the HTTP status code into the $httpStatus variable
$response = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers -ContentType "application/json" -SkipHttpErrorCheck -StatusCodeVariable httpStatus

# 5. Handle the response based on the status code
if ($httpStatus -ge 200 -and $httpStatus -lt 300) {
    Write-Host "Successfully retrieved models (Status: $httpStatus):" -ForegroundColor Green
    # The response object often contains a 'data' property with the list of models
    $response.data | ConvertTo-Json -Depth 5
} 
else {
    # If the status is 400 or above, it's an error
    Write-Host "An error occurred!" -ForegroundColor Red
    Write-Host "Status Code: $httpStatus" -ForegroundColor Red
    
    # Because we skipped the error check, $response now contains the server's error body directly
    $errorBody = $response | ConvertTo-Json -Depth 5 -Compress
    Write-Host "Error Body: $errorBody" -ForegroundColor Red
    
    Write-Host "`nTroubleshooting:" -ForegroundColor Yellow
    Write-Host "A 401 status means your API key is missing, invalid, or expired." -ForegroundColor Yellow
    Write-Host "Run 'echo `$env:GENAI_API_KEY' to verify the exact string being passed." -ForegroundColor Yellow
}
