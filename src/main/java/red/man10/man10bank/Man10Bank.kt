package red.man10.man10bank

import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.loan.Event
import red.man10.man10bank.loan.LoanCommand
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10Bank : JavaPlugin(),Listener {

    companion object{
        const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10Bank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun sendMsg(p: CommandSender, msg: String) {
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.1f",double)
        }

        const val OP = "man10bank.op"
        const val USER = "man10bank.user"

        var fee : Double = 0.00
        var rate : Double = 1.0

        var bankEnable = true

        var loanFee : Double = 1.0
        var loanRate : Double = 1.0
        var loanMax : Double = 10000000.0

    }

    private val checking = HashMap<Player,Command>()

    lateinit var es : ExecutorService

    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        es = Executors.newCachedThreadPool()

        mysqlQueue()

        vault = VaultManager(this)

        fee = config.getDouble("fee",1.0)
        rate = config.getDouble("rate",1.0)

        loanFee = config.getDouble("loanfee",1.1)
        loanMax = config.getDouble("loanmax",10000000.0)
        loanRate = config.getDouble("loanrate",1.0)

        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(Event(),this)

        getCommand("mlend")!!.setExecutor(LoanCommand())

    }

    override fun onDisable() {
        // Plugin shutdown logic
        es.shutdown()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label == "mbaltop"){

            if (sender !is Player)return false

            sendMsg(sender,"§e§l現在取得中....")

            es.execute{

                val list = Bank.balanceTop()?:return@execute

                for (data in list){
                    sendMsg(sender,"§b§l${data.first.name} : §e§l$ ${format(data.second)}")
                }

                sendMsg(sender,"§e§l合計口座残高")

                sendMsg(sender,"§b§kXX§e§l${format(Bank.totalBalance())}§b§kXX")

                sendMsg(sender,"§e§l平均口座残高")

                sendMsg(sender,"§b§kXX§e§l${format(Bank.average())}§b§kXX")

            }

        }

        if (label == "mbal"){

            if (args.isEmpty()){

                if (sender !is Player)return false

                sendMsg(sender,"現在取得中....")

                es.execute{
                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${format(Bank.getBalance(sender.uniqueId))}§b§kXX")

                }

                return true
            }

            val cmd = args[0]

            if (cmd == "user"&& args.size == 2){
                if (sender !is Player)return false
                if (!sender.hasPermission(OP))return true

                sendMsg(sender,"現在取得中....")

                es.execute{
                    val uuid = Bank.getUUID(args[1])

                    if (uuid == null){
                        sendMsg(sender,"まだ口座が解説されていない可能性があります")
                        return@execute
                    }

                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${format(Bank.getBalance(uuid))}§b§kXX")

                }

            }

            if (!sender.hasPermission(USER))return true

            if (cmd == "help"){

                if (sender !is Player)return false
                sendMsg(sender,"§eMan10Bank")
                sendMsg(sender,"§e/mbal : 口座残高を確認する")
                sendMsg(sender,"§e/mbal deposit(d) <金額>: 所持金のいくらかを、口座に入金する")
                sendMsg(sender,"§e/mbal withdraw(w) <金額>: 口座のお金を、出金する")
            }

            if (cmd == "log"){

                if (sender !is Player)return false

                sendMsg(sender,"計算中...")

                es.execute{

                    val deposit = format(Bank.calcLog(true,sender))
                    val withdraw = format(Bank.calcLog(false,sender))

                    sendMsg(sender,"§e§l今月の入出金額の合計")

                    sendMsg(sender,"§a§l入金額:${deposit}")
                    sendMsg(sender,"§c§l出金額:${withdraw}")

                }

                return true
            }

            //deposit withdraw
            if ((cmd == "deposit" || cmd == "d")&& args.size == 2){

                if (sender !is Player)return false

                if (!isEnabled)return false


                //入金額
                val amount : Double = if (args[1] == "all"){
                    vault.getBalance(sender.uniqueId)
                }else{
                    if (!NumberUtils.isNumber(args[1])){
                        sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                        return true
                    }
                    args[1].toDouble()
                }

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l入金失敗！、所持金が足りません！")
                    return true

                }

                es.execute {
                    Bank.deposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand")

                    sendMsg(sender,"§a§l入金成功！")
                }


                return true
            }

            if ((cmd == "withdraw" || cmd == "w") && args.size == 2){
                if (sender !is Player)return false

                if (!isEnabled)return false

                es.execute {

                    var amount = if (args[1] == "all"){
                        Bank.getBalance(sender.uniqueId)*rate
                    }else{
                        if (!NumberUtils.isNumber(args[1])){
                            sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                            return@execute
                        }
                        args[1].toDouble()
                    }

                    if (amount < 0){
                        sendMsg(sender,"§c§l0未満の値は出金出来ません！")
                        return@execute
                    }

                    if (!Bank.withdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand")){
                        sendMsg(sender,"§c§l出金失敗！口座残高が足りません！")
                        return@execute
                    }

                    val fee1 = (amount*fee)
                    amount -= fee1

                    vault.deposit(sender.uniqueId,amount)

                    sendMsg(sender,"§a§l出金成功！")
                    if (fee1 != 0.0){
                        sendMsg(sender,"§a$${format(fee1)}手数料を徴収しました")
                    }

                }

                return true
            }

            if (cmd == "take"){//mbal take <name> <amount>

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l回収する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 0){
                    sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid = Bank.getUUID(args[1])?: return@execute

                    if (!Bank.withdraw(uuid,amount,this,"TakenByCommand")){
                        Bank.setBalance(uuid,0.0)
                        sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                        return@execute
                    }
                    sendMsg(sender,"§a${format(amount)}円回収しました")
                    sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                }
                return true

            }

            if (cmd == "give"){

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 0){
                    sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid =  Bank.getUUID(args[1])?: return@execute

                    Bank.deposit(uuid,amount,this,"GivenFromServer")

                    sendMsg(sender,"§a${format(amount)}円入金しました")
                    sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                }
            }

            if (cmd == "set"){

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l設定する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 0){
                    sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid =  Bank.getUUID(args[1])?: return@execute

                    Bank.setBalance(uuid,amount)

                    sendMsg(sender,"§a${format(amount)}円に設定しました")

                }
            }

            if (cmd == "off"){
                if (!sender.hasPermission(OP))return false

                bankEnable = false
                Bukkit.broadcastMessage("§e§lMan10Bankが閉じました！")
                return true

            }

            if (cmd == "on"){
                if (!sender.hasPermission(OP))return false

                bankEnable = true

                Bukkit.broadcastMessage("§e§lMan10Bankが開きました！")
                return true

            }

            if (cmd == "reload"){
                if (!sender.hasPermission(OP))return false

                es.execute{
                    reloadConfig()

                    fee = config.getDouble("fee")
                    rate = config.getDouble("rate")
                    loanMax = config.getDouble("loanmax")
                    loanFee = config.getDouble("loanfee")

                    Bank.reload()
                }

            }


        }


        if (sender !is Player)return false


        if (label == "pay"){
            if (!sender.hasPermission(USER))return true

            if (!isEnabled)return false

            if (args.size != 2)return false

            if (!NumberUtils.isNumber(args[1])){
                sendMsg(sender,"§c§l/pay <ユーザー名> <金額>")
                return true
            }

            val amount = args[1].toDouble()

            if (amount <0){
                sendMsg(sender,"§c§l0未満の額は送金できません！")
                return true
            }

            if (checking[sender] == null||checking[sender]!! != command){

                sendMsg(sender,"§7§l送金金額:${format(amount)}")
                sendMsg(sender,"§7§l送金相手:${args[0]}")
                sendMsg(sender,"§7§l確認のため、もう一度入力してください")

                checking[sender] = command

                return true
            }

            checking.remove(sender)

            sendMsg(sender,"§e§l現在入金中・・・")

            val p = Bukkit.getPlayer(args[0])

            if (p==null){
                sendMsg(sender,"§c§l送金相手がオフラインの可能性があります")
                return true
            }

            if (!vault.withdraw(sender.uniqueId,amount)){
                sendMsg(sender,"§c§l送金する残高が足りません！")
                return true
            }

            vault.deposit(p.uniqueId,amount)

            sendMsg(sender,"§a§l送金成功！")
            sendMsg(p,"§a${sender.name}から${format(amount)}円送金されました")
        }

        if (label == "mpay"){

            if (!sender.hasPermission(USER))return true

            if (!isEnabled)return false

            if (args.size != 2)return false

            if (!NumberUtils.isNumber(args[1])){
                sendMsg(sender,"§c§l/mpay <ユーザー名> <金額>")
                return true
            }

            val amount = args[1].toDouble()

            if (amount <0){
                sendMsg(sender,"§c§l0未満の額は送金できません！")
                return true
            }

            if (checking[sender] == null||checking[sender]!! != command){

                sendMsg(sender,"§7§l送金金額:${format(amount)}")
                sendMsg(sender,"§7§l送金相手:${args[0]}")
                sendMsg(sender,"§7§l確認のため、もう一度入力してください")

                checking[sender] = command

                return true
            }

            checking.remove(sender)

            sendMsg(sender,"§e§l現在入金中・・・")


            es.execute {
                val uuid = Bank.getUUID(args[0])

                if (uuid == null){
                    sendMsg(sender,"§c§l存在しないユーザ、もしくは口座がありません！")
                    return@execute
                }


                if (!Bank.withdraw(sender.uniqueId,amount,this,"RemittanceTo${args[0]}")){
                    sendMsg(sender,"§c§l送金する残高が足りません！")
                    return@execute

                }

                Bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}")

                sendMsg(sender,"§a§l送金成功！")

                val p = Bukkit.getPlayer(uuid)?:return@execute
                sendMsg(p,"§a${sender.name}から${format(amount)}円送金されました")
            }
        }

        return false
    }


    @EventHandler
    fun login(e:PlayerJoinEvent){
        e.player.performCommand("mbal")
        Bank.changeName(e.player)
    }
}