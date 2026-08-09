OSRS FLIP DESK — SIMPLE TEST SETUP (WINDOWS)

The plugin itself is simple to use. Because it is not published on the RuneLite Plugin Hub yet,
RuneLite's official workflow is to test it in a development client first.

1. Install IntelliJ IDEA Community Edition.
2. Install Java 11 (Eclipse Temurin 11 works).
3. Extract this ZIP.
4. In IntelliJ: File > Open > select the OSRS_Flip_Desk_RuneLite folder.
5. Let IntelliJ import it as a Gradle project.
6. Open the Gradle panel on the right.
7. Run: Tasks > other > run
   A RuneLite development client should open with an "OSRS Flip Desk" sidebar icon.

IF YOU USE A JAGEX ACCOUNT:
RuneLite's dev-client instructions require credentials to be written by the official launcher once.

A. Open the Windows Start menu and run "RuneLite (configure)".
B. Add this Client argument:
   --insecure-write-credentials
C. Save.
D. Launch RuneLite once through the Jagex Launcher.
E. Close it, then launch the development client from IntelliJ using the Gradle "run" task.

USING THE PLUGIN:
- Enter your actual GP in "GP on hand".
- Press Enter or click Refresh.
- Use the green BUY target and blue SELL target.
- Unaffordable items are automatically excluded.
- Your actual GE fills are observed locally and gradually tune future recommendations.
- The plugin never clicks, types into, or submits a Grand Exchange offer for you.

Read README.md for the pricing model and privacy details.


NEW IN v1.1:
- Recommended buy fills are automatically logged as Active flips.
- Partial buys are accumulated with a weighted average buy price.
- The plugin determines and stores a recommended sell target automatically.
- Matching sell fills automatically realize profit.
- Partial sells are supported.
- Fully sold positions move to History automatically.
- Total realized flip profit is tracked at the top of the panel.
