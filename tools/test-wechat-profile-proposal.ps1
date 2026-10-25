param([string]$OutDir = (Join-Path (Split-Path -Parent $PSScriptRoot) '.debug-artifacts/profile-proposal-tests'))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'wechat-profile-proposal.ps1')
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$proposal = New-WeChatReplyProfileProposal -VersionName '8.0.77' -VersionCode 3160 -HelperClass 'sample.Input' -GateClass 'sample.Gate' -GateMethods @('a','b')
$source = @'
public final class ProfileProposalFixture {
    private static final String RECEIVER_CLASS = "sample.Receiver";
    private static final class WeChatReplyProfile {
        final String label;
        WeChatReplyProfile(String label, String[] receivers, String[] helpers, String[] gates, String[] methods) { this.label = label; }
    }
__CONSTANTS__
    private static WeChatReplyProfile select(String versionName, long versionCode) {
__MAPPINGS__
        return null;
    }
    public static void main(String[] args) {
        if (select("8.0.77", 3160) == null) throw new AssertionError("exact build rejected");
        if (select("8.0.77", 3141) != null) throw new AssertionError("different build accepted");
        if (select("8.0.78", 3160) != null) throw new AssertionError("different version accepted");
        if (select("8.0.77.dev", 3160) != null) throw new AssertionError("unverified variant accepted");
    }
}
'@
$source = $source.Replace('__CONSTANTS__', ($proposal.ConstLines -join "`n")).Replace('__MAPPINGS__', $proposal.MappingLine)
$javaFile = Join-Path $OutDir 'ProfileProposalFixture.java'
Set-Content -LiteralPath $javaFile -Value $source -Encoding utf8NoBOM
& javac -encoding UTF-8 -d $OutDir $javaFile
if ($LASTEXITCODE -ne 0) { throw 'Generated profile did not compile' }
& java -cp $OutDir ProfileProposalFixture
if ($LASTEXITCODE -ne 0) { throw 'Generated profile accepted an unverified version/build pair' }
$other = New-WeChatReplyProfileProposal -VersionName '8.0.77' -VersionCode 3141 -HelperClass 'sample.InputOld' -GateClass 'sample.GateOld' -GateMethods @('c')
$source = $source.Replace(($proposal.ConstLines -join "`n"), (($proposal.ConstLines + $other.ConstLines) -join "`n"))
$source = $source.Replace($proposal.MappingLine, ($proposal.MappingLine + "`n" + $other.MappingLine))
$source = $source.Replace('if (select("8.0.77", 3141) != null) throw new AssertionError("different build accepted");',
    'if (select("8.0.77", 3141) == null || !"wechat-8.0.77-3141".equals(select("8.0.77", 3141).label)) throw new AssertionError("second build mapping lost");')
Set-Content -LiteralPath $javaFile -Value $source -Encoding utf8NoBOM
& javac -encoding UTF-8 -d $OutDir $javaFile
if ($LASTEXITCODE -ne 0) { throw 'Same-name builds produced conflicting constants' }
& java -cp $OutDir ProfileProposalFixture
if ($LASTEXITCODE -ne 0) { throw 'Same-name builds did not preserve independent mappings' }
'PASS: exact profile matching, rejected variants, and distinct build constants'
