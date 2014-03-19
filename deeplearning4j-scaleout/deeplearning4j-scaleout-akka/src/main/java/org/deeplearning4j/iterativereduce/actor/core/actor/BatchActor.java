package org.deeplearning4j.iterativereduce.actor.core.actor;

import org.apache.commons.lang3.SerializationUtils;
import org.deeplearning4j.datasets.DataSet;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.iterativereduce.actor.core.ClusterListener;
import org.deeplearning4j.iterativereduce.actor.core.DoneMessage;
import org.deeplearning4j.iterativereduce.actor.core.MoreWorkMessage;
import org.deeplearning4j.iterativereduce.actor.core.ResetMessage;
import org.deeplearning4j.iterativereduce.actor.multilayer.MasterActor;
import org.deeplearning4j.iterativereduce.tracker.statetracker.StateTracker;
import org.deeplearning4j.scaleout.conf.Conf;
import org.deeplearning4j.scaleout.iterativereduce.multi.UpdateableImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.contrib.pattern.DistributedPubSubMediator;

/**
 * Handles the data set iterator.
 * This includes disseminating new data sets to the cluster.
 * @author Adam Gibson
 *
 */
public class BatchActor extends UntypedActor {

	protected DataSetIterator iter;
	private final ActorRef mediator = DistributedPubSubExtension.get(getContext().system()).mediator();
	private static Logger log = LoggerFactory.getLogger(BatchActor.class);
	public final static String FINETUNE = "finetune";
	private StateTracker<UpdateableImpl> stateTracker;
	private Conf conf;
	
	
	public BatchActor(DataSetIterator iter,StateTracker<UpdateableImpl> stateTracker,Conf conf) {
		this.iter = iter;
		this.stateTracker = stateTracker;
		this.conf = conf;
		//subscribe to shutdown messages
		mediator.tell(new DistributedPubSubMediator.Subscribe(MasterActor.SHUTDOWN, getSelf()), getSelf());

	}


	@Override
	public void onReceive(Object message) throws Exception {
		if(message instanceof DistributedPubSubMediator.SubscribeAck || message instanceof DistributedPubSubMediator.UnsubscribeAck) {
			log.info("Susbcribed batch actor");
			mediator.tell(new DistributedPubSubMediator.Publish(ClusterListener.TOPICS,
					message), getSelf());	
		}
		else if(message instanceof ResetMessage) {
			iter.reset();
			
			if(iter.hasNext()) {
				log.info("Propagating new work to master");
				mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
						iter.next()), mediator);
			}
			else if(!iter.hasNext()) {
				mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
						DoneMessage.getInstance()), mediator);
			}
		}

		else if(message instanceof MoreWorkMessage) {
			MoreWorkMessage m = (MoreWorkMessage) message;
			UpdateableImpl result = (UpdateableImpl) m.getUpdateable();
			UpdateableImpl save = SerializationUtils.clone(result);
			log.info("Saving model");
			mediator.tell(new DistributedPubSubMediator.Publish(ModelSavingActor.SAVE,
					save), mediator);

			if(iter.hasNext()) {
				log.info("Propagating new work to master");
		
				/*
				 * Ideal number is target mini batch size per worker.
				 * 
				 * 
				 */
				int numWorkers = stateTracker.jobIds().size();
				int miniBatchSize = conf.getSplit();
		

				//fetch specified batch
				int batch = numWorkers * miniBatchSize;
				
				DataSet next = iter.next(batch);
				
				
				mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
						next), mediator);
			}
			else if(!iter.hasNext()) {
				mediator.tell(new DistributedPubSubMediator.Publish(MasterActor.MASTER,
						DoneMessage.getInstance()), mediator);
			}


			else
				unhandled(message);
		}
	}



	public DataSetIterator getIter() {
		return iter;
	}

}