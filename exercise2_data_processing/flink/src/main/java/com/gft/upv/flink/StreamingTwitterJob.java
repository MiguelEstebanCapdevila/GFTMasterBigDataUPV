
package com.gft.upv.flink;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.flink.formats.json.JsonNodeDeserializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.elasticsearch7.ElasticsearchSink;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011;
import org.apache.http.HttpHost;

import com.gft.upv.config.AppConfig;
import com.gft.upv.flink.process.ExtendedElasticSink;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;



/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingTwitterJob {

	private String topic;
	private AppConfig appConfig;
	private Properties jobProperties=new Properties();
	private StreamExecutionEnvironment env;
	
	
	public StreamingTwitterJob(String[] args) {
		this.topic=args[0];
	}
	
	public void setupJob() throws Exception {
	
		ClassLoader classLoader = StreamingTwitterJob.class.getClassLoader();
			
		//Load application configuration
		InputStream is=classLoader.getResourceAsStream("application.conf");
		InputStreamReader reader = new InputStreamReader(is);
		Config config = ConfigFactory.parseReader(reader);
		appConfig = new AppConfig(config);
				
		//Setup job properties
		jobProperties.put("bootstrap.servers", appConfig.getKafkaConf().getKafkaBrokersUrls());
		// only required for Kafka 0.8
		jobProperties.put("zookeeper.connect", appConfig.getKafkaConf().getZkUrl());
		jobProperties.setProperty("group.id", "stockConsumer");
		jobProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		jobProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		
		
	}
	
	public void createPipeline() {
		
		// set up the streaming execution environment
		env = StreamExecutionEnvironment.getExecutionEnvironment();

				
		JsonNodeDeserializationSchema serde = new JsonNodeDeserializationSchema();
		DataStream<ObjectNode> twitterStream = env
			.addSource(new FlinkKafkaConsumer011<>(this.topic, serde, jobProperties));
			
			
		List<HttpHost> httpHosts = new ArrayList<>();
		httpHosts.add(new HttpHost(this.appConfig.getElasticConf().getHost(), this.appConfig.getElasticConf().getPort(), "http"));
		
		// use a ElasticsearchSink.Builder to create an ElasticsearchSink
		ElasticsearchSink.Builder<ObjectNode> esSinkBuilder = new ElasticsearchSink.Builder<>(
		    httpHosts,
		    new ExtendedElasticSink(this.appConfig.getElasticConf().getTwitterIndex()));
				
				
		// configuration for the bulk requests; this instructs the sink to emit after every element, otherwise they would be buffered
		esSinkBuilder.setBulkFlushMaxActions(1);

		

		// finally, build and add the sink to the job's pipeline
		twitterStream.addSink(esSinkBuilder.build());
		
		//tradesTransformed.addSink(new FlinkKafkaProducer010<>("tradesTransformed", serde, jobProperties));
	}
	
	public void launchPipeline() throws Exception{
		this.env.execute();
	}
	
	public static void main(String[] args) throws Exception {
		
		if(args.length!=1) {
			System.out.println("Error, you have not specified the right parameters");
			System.out.println("--------------------------------------------------");
			
			System.out.println("Example: StreamingStockJob topic ");
			System.out.println("topic: Kafka topic where you want to consume the messages ");		
		}
		else
		{
			StreamingTwitterJob streamingJob=new StreamingTwitterJob(args);
			streamingJob.setupJob();
			streamingJob.createPipeline();
			streamingJob.launchPipeline();
		}
		
	
	}
}
