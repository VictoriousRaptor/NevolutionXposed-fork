function New-WeChatReplyProfileProposal {
    param(
        [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$VersionName,
        [Parameter(Mandatory)][ValidateRange(1, [long]::MaxValue)][long]$VersionCode,
        [Parameter(Mandatory)][string]$HelperClass,
        [Parameter(Mandatory)][string]$GateClass,
        [Parameter(Mandatory)][string[]]$GateMethods
    )
    $constant = 'RELEASE_' + ($VersionName -replace '\.', '_') + "_$VersionCode"
    $label = "wechat-$VersionName-$VersionCode"
    $methods = ($GateMethods | ForEach-Object { '"' + $_ + '"' }) -join ', '
    [pscustomobject]@{
        ConstantName = $constant
        ConstLines = @(
            "`t/** Descriptors probed for WeChat $VersionName/$VersionCode; runtime behavior unverified. */",
            "`tprivate static final WeChatReplyProfile $constant = new WeChatReplyProfile(",
            "`t`t`t`"$label`",",
            "`t`t`tnew String[] { RECEIVER_CLASS },",
            "`t`t`tnew String[] { `"$HelperClass`" },",
            "`t`t`tnew String[] { `"$GateClass`" },",
            "`t`t`tnew String[] { $methods });"
        )
        MappingLine = "`t`tif (`"$VersionName`".equals(versionName) && versionCode == ${VersionCode}L) return $constant;"
    }
}
