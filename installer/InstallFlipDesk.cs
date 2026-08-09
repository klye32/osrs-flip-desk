using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

internal static class Program
{
    private const string ResourceName = "osrs_flip_desk.jar";
    private const string JarFileName = "osrs-flip-desk-1.1.0.jar";

    [STAThread]
    private static void Main()
    {
        try
        {
            string destDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                ".runelite",
                "sideloaded-plugins");
            Directory.CreateDirectory(destDir);

            string destFile = Path.Combine(destDir, JarFileName);

            if (IsRuneLiteRunning())
            {
                DialogResult closeFirst = MessageBox.Show(
                    "RuneLite looks like it is still running.\n\n"
                    + "Close RuneLite completely before installing, then click Retry.\n"
                    + "Or click Ignore to install anyway (you will still need to restart RuneLite).",
                    "OSRS Flip Desk Installer",
                    MessageBoxButtons.AbortRetryIgnore,
                    MessageBoxIcon.Warning);

                if (closeFirst == DialogResult.Abort)
                {
                    return;
                }

                if (closeFirst == DialogResult.Retry && IsRuneLiteRunning())
                {
                    MessageBox.Show(
                        "RuneLite is still running. Close it, then run the installer again.",
                        "OSRS Flip Desk Installer",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Information);
                    return;
                }
            }

            foreach (string oldJar in Directory.GetFiles(destDir, "osrs-flip-desk*.jar"))
            {
                if (!string.Equals(oldJar, destFile, StringComparison.OrdinalIgnoreCase))
                {
                    try
                    {
                        File.Delete(oldJar);
                    }
                    catch
                    {
                        // Keep going; old file may be locked.
                    }
                }
            }

            using (Stream src = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName))
            {
                if (src == null)
                {
                    throw new InvalidOperationException(
                        "Installer is missing the embedded plugin JAR. Rebuild with build-installer.bat.");
                }

                using (FileStream dst = File.Create(destFile))
                {
                    src.CopyTo(dst);
                }
            }

            MessageBox.Show(
                "Installed OSRS Flip Desk to:\n\n"
                + destFile
                + "\n\nNext steps:\n"
                + "1. Launch RuneLite (or restart it if it was open)\n"
                + "2. Open the sidebar and look for OSRS Flip Desk\n"
                + "3. Enable the plugin if it is not already on",
                "OSRS Flip Desk Installed",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "Install failed:\n\n" + ex.Message,
                "OSRS Flip Desk Installer",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
    }

    private static bool IsRuneLiteRunning()
    {
        string[] names = { "RuneLite", "RuneLite (x86)" };
        foreach (string name in names)
        {
            if (Process.GetProcessesByName(name).Length > 0)
            {
                return true;
            }
        }

        return false;
    }
}
