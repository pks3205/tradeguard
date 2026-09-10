using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;

namespace InternetAccessControl;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.Run(new MainForm());
    }
}

internal sealed class MainForm : Form
{
    private const string RulePrefix = "IAC-Block-";

    private readonly ListView listView = new();
    private readonly Label status = new();
    private readonly string configDir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "InternetAccessControl");
    private readonly string configFile = "";

    public MainForm()
    {
        configFile = Path.Combine(configDir, "state.json");

        Text = "Internet Access Control";
        ClientSize = new System.Drawing.Size(720, 540);
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new System.Drawing.Size(640, 460);
        Font = new System.Drawing.Font("Segoe UI", 9f);

        var header = new Label
        {
            Text = "CHECKED = Internet BLOCKED      UNCHECKED = Internet ALLOWED",
            Location = new System.Drawing.Point(12, 10),
            AutoSize = true,
            Font = new System.Drawing.Font("Segoe UI", 10f, System.Drawing.FontStyle.Bold),
            ForeColor = System.Drawing.Color.Firebrick
        };
        Controls.Add(header);

        listView.Location = new System.Drawing.Point(12, 40);
        listView.Size = new System.Drawing.Size(696, 420);
        listView.View = View.Details;
        listView.CheckBoxes = true;
        listView.FullRowSelect = true;
        listView.GridLines = true;
        listView.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        listView.Columns.Add("Application", 240);
        listView.Columns.Add("Program path", 436);
        Controls.Add(listView);

        var btnCheckAll = AddButton("Check All", 12, 100);
        var btnUncheckAll = AddButton("Uncheck All", 118, 100);
        var btnRefresh = AddButton("Refresh", 224, 90);
        var btnApply = AddButton("Apply Rules", 320, 110);
        var btnAdd = AddButton("Add program...", 436, 110);
        var btnDone = AddButton("Done", 552, 90);

        status.Location = new System.Drawing.Point(12, 506);
        status.AutoSize = true;
        status.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
        Controls.Add(status);

        btnCheckAll.Click += (_, _) => SetAll(true, "All checked = everything will be BLOCKED after Apply.");
        btnUncheckAll.Click += (_, _) => SetAll(false, "All unchecked = everything keeps internet after Apply.");
        btnRefresh.Click += (_, _) => Reload();
        btnApply.Click += (_, _) => ApplyRules();
        btnAdd.Click += (_, _) => AddProgram();
        btnDone.Click += (_, _) => Close();

        Load += (_, _) => Reload();
    }

    private Button AddButton(string text, int x, int width)
    {
        var b = new Button
        {
            Text = text,
            Location = new System.Drawing.Point(x, 468),
            Size = new System.Drawing.Size(width, 32),
            Anchor = AnchorStyles.Bottom | AnchorStyles.Left
        };
        Controls.Add(b);
        return b;
    }

    private void SetAll(bool value, string message)
    {
        foreach (ListViewItem item in listView.Items) item.Checked = value;
        status.Text = message;
    }

    private void AddRow(string path, string name, bool isChecked)
    {
        var item = new ListViewItem(name) { Checked = isChecked, Tag = path };
        item.SubItems.Add(path);
        listView.Items.Add(item);
    }

    private void Reload()
    {
        var current = new Dictionary<string, bool>();
        foreach (ListViewItem item in listView.Items)
            current[(string)item.Tag] = item.Checked;

        listView.Items.Clear();

        var saved = LoadState();
        foreach (var (path, name) in EnumerateApps())
        {
            bool isChecked = saved.TryGetValue(path, out bool s) ? s
                : current.TryGetValue(path, out bool c) ? c : false;
            AddRow(path, name, isChecked);
        }

        status.Text = $"{listView.Items.Count} applications loaded.";
    }

    private void ApplyRules()
    {
        int blocked = 0, allowed = 0, failed = 0;
        var state = new List<AppEntry>();

        foreach (ListViewItem item in listView.Items)
        {
            string path = (string)item.Tag;
            string name = item.Text;
            string ruleName = RulePrefix + Sanitize(name) + "-" + ShortHash(path);

            if (item.Checked)
            {
                int code = RunNetsh(
                    $"advfirewall firewall add rule name=\"{ruleName}\" dir=out action=block program=\"{path}\" enable=yes");
                if (code == 0) blocked++; else failed++;
            }
            else
            {
                RunNetsh($"advfirewall firewall delete rule name=\"{ruleName}\"");
                allowed++;
            }

            state.Add(new AppEntry(path, name, item.Checked));
        }

        Directory.CreateDirectory(configDir);
        File.WriteAllText(configFile, JsonSerializer.Serialize(state, new JsonSerializerOptions { WriteIndented = true }));

        status.Text = $"Applied. Blocked: {blocked}   Allowed: {allowed}   Errors: {failed}";
        MessageBox.Show(this,
            $"Done.\n\nBlocked (no internet): {blocked}\nAllowed (internet on): {allowed}\nErrors: {failed}",
            "Internet Access Control", MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    private void AddProgram()
    {
        using var dlg = new OpenFileDialog
        {
            Filter = "Executable (*.exe)|*.exe|All files (*.*)|*.*",
            Title = "Select a program to add to the list"
        };
        if (dlg.ShowDialog(this) == DialogResult.OK)
        {
            string name = Path.GetFileNameWithoutExtension(dlg.FileName);
            AddRow(dlg.FileName, name, false);
            status.Text = $"Added: {dlg.FileName} (unchecked = internet allowed).";
        }
    }

    private Dictionary<string, string> EnumerateApps()
    {
        var apps = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        // 1) Start Menu shortcuts -> exe target
        var shellType = Type.GetTypeFromProgID("WScript.Shell");
        if (shellType != null)
        {
            dynamic shell = Activator.CreateInstance(shellType)!;
            var startDirs = new[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu), "Programs"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs")
            };
            foreach (var dir in startDirs.Where(Directory.Exists))
            {
                foreach (var lnk in Directory.EnumerateFiles(dir, "*.lnk", SearchOption.AllDirectories))
                {
                    try
                    {
                        object? targetObj = shell.CreateShortcut(lnk).TargetPath;
                        if (targetObj is string target
                            && target.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
                            && File.Exists(target))
                        {
                            apps[target] = Path.GetFileNameWithoutExtension(lnk);
                        }
                    }
                    catch
                    {
                        // ignore unreadable shortcuts
                    }
                }
            }
        }

        // 2) Currently running programs (Windows system folders excluded)
        foreach (var p in Process.GetProcesses())
        {
            try
            {
                string? path = p.MainModule?.FileName;
                if (!string.IsNullOrEmpty(path)
                    && path.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
                    && !path.StartsWith(Environment.GetFolderPath(Environment.SpecialFolder.Windows), StringComparison.OrdinalIgnoreCase)
                    && File.Exists(path))
                {
                    apps[path] = p.ProcessName;
                }
            }
            catch
            {
                // access denied to some system processes
            }
        }

        return apps.OrderBy(kv => kv.Value, StringComparer.OrdinalIgnoreCase)
                   .ToDictionary(kv => kv.Key, kv => kv.Value, StringComparer.OrdinalIgnoreCase);
    }

    private Dictionary<string, bool> LoadState()
    {
        try
        {
            if (File.Exists(configFile))
            {
                var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
                var list = JsonSerializer.Deserialize<List<AppEntry>>(File.ReadAllText(configFile), options) ?? new();
                return list.ToDictionary(e => e.Path, e => e.Checked, StringComparer.OrdinalIgnoreCase);
            }
        }
        catch
        {
            // corrupt state -> start fresh
        }
        return new Dictionary<string, bool>();
    }

    private static int RunNetsh(string arguments)
    {
        var psi = new ProcessStartInfo
        {
            FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "netsh.exe"),
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        using var p = Process.Start(psi)!;
        p.WaitForExit();
        return p.ExitCode;
    }

    private static string Sanitize(string name)
    {
        var sb = new StringBuilder();
        foreach (char c in name)
            if (char.IsLetterOrDigit(c) || c is '_' or '-' or ' ') sb.Append(c);
        return sb.ToString();
    }

    private static string ShortHash(string value)
    {
        using var md5 = MD5.Create();
        byte[] hash = md5.ComputeHash(Encoding.UTF8.GetBytes(value));
        return Convert.ToHexString(hash)[..8].ToLowerInvariant();
    }

    private sealed record AppEntry(string Path, string Name, bool Checked);
}
