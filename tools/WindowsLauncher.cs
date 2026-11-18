using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;
using System.Reflection;

[assembly: AssemblyTitle("The Sunken Seal")]
[assembly: AssemblyProduct("The Sunken Seal")]
[assembly: AssemblyVersion("1.0.0.0")]

// Windows convenience launcher only. The game itself remains a standalone Java JAR.
internal static class WindowsLauncher
{
    [STAThread]
    private static void Main()
    {
        string directory = AppDomain.CurrentDomain.BaseDirectory;
        string jar = Path.Combine(directory, "game.jar");
        string java = Path.Combine(directory, "java", "bin", "javaw.exe");
        if (!File.Exists(java))
        {
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            java = String.IsNullOrEmpty(javaHome) ? "javaw.exe" : Path.Combine(javaHome, "bin", "javaw.exe");
        }
        try
        {
            if (!File.Exists(jar)) throw new FileNotFoundException("Keep game.jar beside this launcher.");
            Process.Start(new ProcessStartInfo
            {
                FileName = java,
                Arguments = "-jar \"" + jar + "\"",
                WorkingDirectory = directory,
                UseShellExecute = false,
                CreateNoWindow = true
            });
        }
        catch (Exception exception)
        {
            MessageBox.Show("The adventure could not start. Keep the bundled java folder beside the launcher, " +
                "or install Java 17 or newer.\n\n" + exception.Message, "The Sunken Seal",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
