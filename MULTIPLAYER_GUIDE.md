# 🏁 GridRush F1 - Multiplayer Hosting & Connection Guide

GridRush F1 features an ultra-low latency, real-time multiplayer system powered by custom **UDP sockets** on **Port 9876**. This guide walks you through hosting your own server and having friends join you from their own computers.

---

## 🗺️ Multiplayer Connection Options

You can play with other people using three different methods, ranging from the simplest (same house/room) to internet-wide setups.

### Option 1: Local Area Network (LAN) — *Easiest for same-network play*
Use this if you and your friends are connected to the **same Wi-Fi router** or local network.

### Option 2: Virtual LAN (ZeroTier / Tailscale) — *Easiest for internet play*
If your friends are in different homes, instead of struggling with complicated router settings, you can create a secure virtual network.
1. Download a free tool like **ZeroTier** (https://www.zerotier.com) or **Tailscale** (https://tailscale.com) on all computers.
2. Create a private network and have everyone join it.
3. Use the virtual IP address assigned to the host by the tool to connect!

### Option 3: Public WAN Port Forwarding — *For direct internet play*
If you want to host directly on your public IP:
1. Log into your home router's admin panel.
2. Forward **Port 9876 (UDP)** to your host machine's local IP address.
3. Share your public IP (find it on https://icanhazip.com) with your friends.

---

## 🖥️ Step-by-Step Hosting Instructions

### Step 1: Allow GridRush through the Windows Firewall (CRITICAL 🛡️)
By default, Windows blocks incoming UDP sockets. The **Host** must do this once:
1. Press the **Windows Key**, type `Firewall`, and select **Windows Defender Firewall with Advanced Security**.
2. Click **Inbound Rules** in the left sidebar, then click **New Rule...** on the right side.
3. Select **Port** and click Next.
4. Choose **UDP** and enter **9876** in *Specific local ports*, then click Next.
5. Select **Allow the connection** and click Next.
6. Check **Domain, Private, and Public**, click Next.
7. Name it `GridRush F1 Server` and click **Finish**.

### Step 2: Find Your Host IP Address
1. Open **PowerShell** or **Command Prompt** on the hosting computer.
2. Run the command:
   ```powershell
   ipconfig
   ```
3. Look for your active network adapter (e.g., *Wireless LAN adapter Wi-Fi* or *Ethernet adapter*).
4. Find the **IPv4 Address** (typically looks like `192.168.1.XX` or `10.0.0.XX`). **Copy this IP!**

### Step 3: Start the Game Server
On the hosting computer, run:
```powershell
.\mvnw.cmd clean compile exec:java "-Dexec.mainClass=GameServer"
```
*Note: Make sure your `JAVA_HOME` is set to Java 17 as detailed in `SETUP_GUIDE.md`.*

---

## 🏎️ How Other Players Join

Once the host's server is running:

1. **Other Players** launch their game client:
   ```powershell
   .\mvnw.cmd javafx:run
   ```
2. On the Main Menu, click **MULTIPLAYER (2 PLAYERS)** or **MULTIPLAYER (4 PLAYERS)**.
3. A sleek dialog box will pop up:
   > **Join Multiplayer Lobby**
   > *Enter Server IP Address:*
4. Type the **Host's IP Address** (which the host found in Step 2) and click **OK**!
5. Select your teams, hit **READY**, and you are on the grid!
