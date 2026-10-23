param(
    [string]$Expected = "build\evaluation\ground-truth-labels-formatted.txt",
    [string]$Predicted = "build\evaluation\output.txt",
    [string]$Scores = "build\evaluation\run-label-scores.txt"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName Microsoft.VisualBasic

function Normalize-Url($url) {
    if ([string]::IsNullOrWhiteSpace($url)) { return "" }
    $u = $url.Trim()
    if ($u -notmatch "^https?://") { $u = "https://" + $u }
    return $u.TrimEnd("/")
}

function Entry-Key($seen, $url) {
    $u = Normalize-Url $url
    if ([string]::IsNullOrWhiteSpace($u)) { return "" }

    if (-not $seen.ContainsKey($u)) {
        $seen[$u] = 0
    }

    $seen[$u]++
    return "$u#$($seen[$u])"
}

function Normalize-QuotedValue($value) {
    if ($null -eq $value) { return "" }
    return $value.Trim()
}

function Normalize-Instance($label) {
    if ([string]::IsNullOrWhiteSpace($label)) { return "" }

    $s = $label.Trim()

    if ($s -eq "(none)" -or $s -ieq "none" -or $s -ieq "no") { return "" }

    $s = [regex]::Replace($s, "'([^']*)'", {
        param($m)
        "'" + (Normalize-QuotedValue $m.Groups[1].Value) + "'"
    })

    return ($s -replace "\s+", " ").Trim()
}

function Instances-From-LabelText($text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return @() }

    @(
        $text -split "\s*\|\s*" |
            ForEach-Object { Normalize-Instance $_ } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Instance-Details-From-LabelText($text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return @() }

    @(
        $text -split "\s*\|\s*" |
            ForEach-Object {
                $raw = $_.Trim()
                $normalized = Normalize-Instance $raw

                if (-not [string]::IsNullOrWhiteSpace($normalized)) {
                    [pscustomobject]@{
                        normalized_instance = $normalized
                        raw_instance = $raw
                    }
                }
            }
    )
}

function Misclassified-Node($instance) {
    if ([string]::IsNullOrWhiteSpace($instance)) { return "" }

    if ($instance -match "'([^']*)'") {
        return $matches[1]
    }

    return ""
}

function Refactoring-Type($instance) {
    if ([string]::IsNullOrWhiteSpace($instance)) { return "" }

    $s = $instance.Trim()

    if ($s -match "^([A-Z_]+)\s*:") {
        $type = $matches[1]
    } else {
        $type = ($s -split ":", 2)[0]
        $type = ($type -replace "\s+", "_").ToUpperInvariant()
    }

    if ($type -eq "SCHEDULE_TASKS") {
        return "SCHEDULING_TASKS"
    }

    if ($type -eq "REPLACE_HARDCODED") {
        return "REPLACE_HARDCODED_VALUE"
    }

    return $type
}

function Read-RunFormat($path) {
    $out = @{}
    $seen = @{}

    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -notmatch "^\s*(\d+)/\d+\s+(\S+)\s+->\s+(.*)$") {
            continue
        }

        $key = Entry-Key $seen $matches[2]
        if ([string]::IsNullOrWhiteSpace($key)) { continue }

        $out[$key] = @(Instances-From-LabelText $matches[3])
    }

    return $out
}

function Read-CsvRecords($path) {
    $parser = [Microsoft.VisualBasic.FileIO.TextFieldParser]::new((Resolve-Path $path))
    $parser.TextFieldType = [Microsoft.VisualBasic.FileIO.FieldType]::Delimited
    $parser.SetDelimiters(",")
    $parser.HasFieldsEnclosedInQuotes = $true

    $headers = $parser.ReadFields()
    for ($i = 0; $i -lt $headers.Length; $i++) {
        if ([string]::IsNullOrWhiteSpace($headers[$i])) {
            $headers[$i] = "H" + ($i + 1)
        }
    }

    $rows = @()
    while (-not $parser.EndOfData) {
        $fields = $parser.ReadFields()
        $obj = [ordered]@{}

        for ($i = 0; $i -lt $headers.Length; $i++) {
            $obj[$headers[$i]] = if ($i -lt $fields.Length) { $fields[$i] } else { "" }
        }

        $rows += [pscustomobject]$obj
    }

    $parser.Close()
    return $rows
}

function Read-Predictions($path) {
    $firstRunLine = Get-Content -LiteralPath $path |
        Where-Object { $_ -match "^\s*\d+/\d+\s+\S+\s+->\s+" } |
        Select-Object -First 1

    if ($firstRunLine) {
        return Read-RunFormat $path
    }

    $out = @{}
    $seen = @{}

    foreach ($row in Read-CsvRecords $path) {
        $url = Normalize-Url $row.commit_url
        if ([string]::IsNullOrWhiteSpace($url)) {
            $url = Normalize-Url $row.url
        }
        if ([string]::IsNullOrWhiteSpace($url)) {
            continue
        }

        $key = Entry-Key $seen $url
        $out[$key] = @(Instances-From-LabelText $row.detected_refactorings)
    }

    return $out
}

function Read-Prediction-Details($path) {
    $firstRunLine = Get-Content -LiteralPath $path |
        Where-Object { $_ -match "^\s*\d+/\d+\s+\S+\s+->\s+" } |
        Select-Object -First 1

    $out = @{}
    $seen = @{}

    if ($firstRunLine) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -notmatch "^\s*(\d+)/\d+\s+(\S+)\s+->\s+(.*)$") {
                continue
            }

            $key = Entry-Key $seen $matches[2]
            if ([string]::IsNullOrWhiteSpace($key)) { continue }

            $out[$key] = @(Instance-Details-From-LabelText $matches[3])
        }

        return $out
    }

    foreach ($row in Read-CsvRecords $path) {
        $url = Normalize-Url $row.commit_url
        if ([string]::IsNullOrWhiteSpace($url)) {
            $url = Normalize-Url $row.url
        }
        if ([string]::IsNullOrWhiteSpace($url)) {
            continue
        }

        $key = Entry-Key $seen $url
        $out[$key] = @(Instance-Details-From-LabelText $row.detected_refactorings)
    }

    return $out
}

function Count-Instances($instances) {
    $counts = @{}

    foreach ($instance in $instances) {
        if ([string]::IsNullOrWhiteSpace($instance)) { continue }

        if (-not $counts.ContainsKey($instance)) {
            $counts[$instance] = 0
        }

        $counts[$instance]++
    }

    return $counts
}

function Multiset-Scores($expected, $predicted) {
    $expectedCounts = Count-Instances $expected
    $predictedCounts = Count-Instances $predicted

    $allKeys = @(
        @($expectedCounts.Keys) + @($predictedCounts.Keys) |
            Sort-Object -Unique
    )

    $tp = 0
    $fp = 0
    $fn = 0

    foreach ($key in $allKeys) {
        $e = if ($expectedCounts.ContainsKey($key)) { [int]$expectedCounts[$key] } else { 0 }
        $p = if ($predictedCounts.ContainsKey($key)) { [int]$predictedCounts[$key] } else { 0 }

        $tp += [Math]::Min($e, $p)
        if ($p -gt $e) { $fp += $p - $e }
        if ($e -gt $p) { $fn += $e - $p }
    }

    [pscustomobject]@{
        TP = $tp
        FP = $fp
        FN = $fn
    }
}

function Metric-Line($tp, $fp, $fn) {
    $precision = if (($tp + $fp) -eq 0) { 0 } else { $tp / ($tp + $fp) }
    $recall = if (($tp + $fn) -eq 0) { 0 } else { $tp / ($tp + $fn) }
    $f1 = if (($precision + $recall) -eq 0) { 0 } else { 2 * $precision * $recall / ($precision + $recall) }

    "Precision={0:N4} Recall={1:N4} F1={2:N4}" -f $precision, $recall, $f1
}

function Add-TypeInstances($bucket, $instances) {
    foreach ($instance in $instances) {
        $type = Refactoring-Type $instance
        if ([string]::IsNullOrWhiteSpace($type)) { continue }

        if (-not $bucket.ContainsKey($type)) {
            $bucket[$type] = @()
        }

        $bucket[$type] += $instance
    }
}
function Add-TypeInstances($bucket, $instances, $rowKey) {
    foreach ($instance in $instances) {
        $type = Refactoring-Type $instance
        if ([string]::IsNullOrWhiteSpace($type)) { continue }

        if (-not $bucket.ContainsKey($type)) {
            $bucket[$type] = @()
        }

        $bucket[$type] += "$rowKey`t$instance"
    }
}
function False-Positive-Instance-Details($expectedDetails, $predictedDetails, $typeName) {
    $remainingExpected = [System.Collections.Generic.List[object]]::new()
    $remainingPredicted = [System.Collections.Generic.List[object]]::new()

    foreach ($detail in $expectedDetails) {
        if ((Refactoring-Type $detail.normalized_instance) -eq $typeName) {
            $remainingExpected.Add($detail)
        }
    }

    foreach ($detail in $predictedDetails) {
        if ((Refactoring-Type $detail.normalized_instance) -ne $typeName) { continue }

        $exactIndex = -1
        for ($i = 0; $i -lt $remainingExpected.Count; $i++) {
            if ($remainingExpected[$i].raw_instance -eq $detail.raw_instance) {
                $exactIndex = $i
                break
            }
        }

        if ($exactIndex -ge 0) {
            $remainingExpected.RemoveAt($exactIndex)
        } else {
            $remainingPredicted.Add($detail)
        }
    }

    $falsePositives = @()

    foreach ($detail in $remainingPredicted) {
        $normalizedIndex = -1
        for ($i = 0; $i -lt $remainingExpected.Count; $i++) {
            if ($remainingExpected[$i].normalized_instance -eq $detail.normalized_instance) {
                $normalizedIndex = $i
                break
            }
        }

        if ($normalizedIndex -ge 0) {
            $remainingExpected.RemoveAt($normalizedIndex)
        } else {
            $falsePositives += $detail
        }
    }

    return $falsePositives
}

$expectedByUrl = Read-RunFormat $Expected
$expectedDetailsByUrl = Read-Prediction-Details $Expected
$predictedByUrl = Read-Predictions $Predicted
$predictedDetailsByUrl = Read-Prediction-Details $Predicted

$tp = 0
$fp = 0
$fn = 0
$rowsCompared = 0
$missingPredictions = 0
$unexpectedPredictionRows = 0

$expectedByType = @{}
$predictedByType = @{}
$removeUnusedFpRows = @()

$allRowKeys = @(
    @($expectedByUrl.Keys) + @($predictedByUrl.Keys) |
        Sort-Object -Unique
)

foreach ($key in $allRowKeys) {
    $expectedInstances = if ($expectedByUrl.ContainsKey($key)) {
        @($expectedByUrl[$key])
    } else {
        @()
    }

    $predictedInstances = if ($predictedByUrl.ContainsKey($key)) {
        @($predictedByUrl[$key])
    } else {
        @()
    }

    if (-not $predictedByUrl.ContainsKey($key)) {
        $missingPredictions++
    }

    if (-not $expectedByUrl.ContainsKey($key)) {
        $unexpectedPredictionRows++
    }

    $rowsCompared++

    $rowScores = Multiset-Scores $expectedInstances $predictedInstances
    $tp += $rowScores.TP
    $fp += $rowScores.FP
    $fn += $rowScores.FN

    Add-TypeInstances $expectedByType $expectedInstances $key
    Add-TypeInstances $predictedByType $predictedInstances $key

    $url = $key -replace "#\d+$", ""
    $predictedDetails = if ($predictedDetailsByUrl.ContainsKey($key)) {
        @($predictedDetailsByUrl[$key])
    } else {
        @()
    }
    $expectedDetails = if ($expectedDetailsByUrl.ContainsKey($key)) {
        @($expectedDetailsByUrl[$key])
    } else {
        @()
    }

    foreach ($predictedRemoveUnusedDetail in False-Positive-Instance-Details $expectedDetails $predictedDetails "REMOVE_UNUSED_CODE") {
        $removeUnusedFpRows += [pscustomobject]@{
            url = $url
            misclassified_node = Misclassified-Node $predictedRemoveUnusedDetail.raw_instance
            predicted_instance = $predictedRemoveUnusedDetail.normalized_instance
            raw_prediction = $predictedRemoveUnusedDetail.raw_instance
        }
    }
}

$removeUnusedFpCounts = @{}

foreach ($predictedRow in $removeUnusedFpRows) {
    $instance = $predictedRow.predicted_instance

    if (-not $removeUnusedFpCounts.ContainsKey($instance)) {
        $removeUnusedFpCounts[$instance] = 0
    }

    $removeUnusedFpCounts[$instance]++
}

$scoreLines = @(
    "Expected instances file: $Expected",
    "Predicted instances file: $Predicted",
    "Rows compared by URL occurrence: $rowsCompared",
    "Expected rows missing predictions: $missingPredictions",
    "Predicted rows missing expected labels: $unexpectedPredictionRows",
    "",
    "Instance-level multiset scores:",
    "TP=$tp FP=$fp FN=$fn",
    (Metric-Line $tp $fp $fn),
    "",
    "Per-type instance-level scores:"
)

$allTypes = @(
    @($expectedByType.Keys) + @($predictedByType.Keys) |
        Sort-Object -Unique
)

foreach ($type in $allTypes) {
    $expectedInstances = if ($expectedByType.ContainsKey($type)) { @($expectedByType[$type]) } else { @() }
    $predictedInstances = if ($predictedByType.ContainsKey($type)) { @($predictedByType[$type]) } else { @() }

    $typeScores = Multiset-Scores $expectedInstances $predictedInstances

    $scoreLines += "{0} TP={1} FP={2} FN={3} {4}" -f `
        $type,
        $typeScores.TP,
        $typeScores.FP,
        $typeScores.FN,
        (Metric-Line $typeScores.TP $typeScores.FP $typeScores.FN)
}

$scoreDir = Split-Path -Parent $Scores
if ($scoreDir) {
    New-Item -ItemType Directory -Force -Path $scoreDir | Out-Null
}

$scoreLines | Set-Content -LiteralPath $Scores -Encoding UTF8
$scoreLines

Write-Output ""
Write-Output "Wrote scores to $Scores"
