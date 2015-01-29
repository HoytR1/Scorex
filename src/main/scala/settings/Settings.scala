package settings

import java.io.File
import java.net.InetAddress
import java.util
import network.Peer
import play.api.libs.json.Json
import scala.util.Try



object Settings {

	//NETWORK
	private val DEFAULT_MIN_CONNECTIONS = 5
	private val DEFAULT_MAX_CONNECTIONS = 20
	private val DEFAULT_CONNECTION_TIMEOUT = 60000
	private val DEFAULT_PING_INTERVAL = 30000
	
	//RPC
	private val DEFAULT_RPC_PORT = 9085
	private val DEFAULT_RPC_ALLOWED = "127.0.0.1"
	
	//DATA
	private val DEFAULT_DATA_DIR = "data"
	private val DEFAULT_WALLET_DIR = "wallet"
	private val DEFAULT_GENERATOR_KEY_CACHING = false
	private val DEFAULT_MAX_BYTE_PER_FEE = 512

	private lazy val settingsJSONTry = Try{
			val jsonString = scala.io.Source.fromFile("settings.json").mkString
			//CREATE JSON OBJECT
			Json.parse(jsonString)
	}

	settingsJSONTry.recover{case _:Throwable =>
			//STOP
			System.out.println("ERROR reading settings.json, closing")
			System.exit(0)
	}

	private lazy val settingsJSON = settingsJSONTry.get

	lazy val getKnownPeers = Try{
		(settingsJSON \ "knownpeers").as[List[String]].flatMap{addr =>
				val address = InetAddress.getByName(addr)
				if (address == InetAddress.getLocalHost) None else Some(new Peer(address))
			}
		}.getOrElse(Seq[Peer]())

	lazy val getMaxConnections = (settingsJSON \ "maxconnections").asOpt[Int].getOrElse(DEFAULT_MAX_CONNECTIONS)

	lazy val getMinConnections = (settingsJSON \ "minconnections").asOpt[Int].getOrElse(DEFAULT_MIN_CONNECTIONS)

	lazy val getConnectionTimeout = (settingsJSON \ "connectiontimeout").asOpt[Int].getOrElse(DEFAULT_CONNECTION_TIMEOUT)

	lazy val getRpcPort = (settingsJSON \ "rpcport").asOpt[Int].getOrElse(DEFAULT_RPC_PORT)

	lazy val getRpcAllowed:Seq[String] = (settingsJSON \ "rpcallowed").asOpt[List[String]].getOrElse(DEFAULT_RPC_ALLOWED.split(""))

	lazy val getWalletDir = (settingsJSON \"walletdir").asOpt[String].getOrElse(DEFAULT_WALLET_DIR)

	lazy val getDataDir = (settingsJSON \ "datadir").asOpt[String].getOrElse(DEFAULT_DATA_DIR)

	lazy val getPingInterval = (settingsJSON \ "pinginterval").asOpt[Int].getOrElse(DEFAULT_PING_INTERVAL)

	lazy val isGeneratorKeyCachingEnabled = (settingsJSON \ "generatorkeycaching").asOpt[Boolean].getOrElse(DEFAULT_GENERATOR_KEY_CACHING)

	lazy val maxBytePerFee = (settingsJSON \"maxbyteperfee").asOpt[Int].getOrElse(DEFAULT_MAX_BYTE_PER_FEE)
}