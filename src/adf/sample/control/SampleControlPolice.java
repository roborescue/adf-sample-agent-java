package adf.sample.control;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.control.ControlPolice;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleControlPolice extends ControlPolice {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandPolice.ACTION_REST;
    private static final int ACTION_MOVE = CommandPolice.ACTION_MOVE;
    private static final int ACTION_CLEAR = CommandPolice.ACTION_CLEAR;

    private Map<EntityID, Task> agentTaskMap;
    private Set<EntityID> request;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {
        this.agentTaskMap = new HashMap<>();
        this.request = new HashSet<>();
        for(StandardEntity entity : worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE)) {
            this.agentTaskMap.put(entity.getID(), new Task(0, ACTION_UNKNOWN, null));
        }
    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {
        this.collectTask(worldInfo, messageManager);
        this.updateAgentTaskInfo(agentInfo, worldInfo, messageManager);
        this.sendCommand(worldInfo, messageManager);
    }

    private void sendCommand(WorldInfo worldInfo, MessageManager messageManager) {
        if(this.request.isEmpty()) {
            return;
        }
        List<StandardEntity> agents = new ArrayList<>();
        for(EntityID id : this.agentTaskMap.keySet()) {
            Task task = this.agentTaskMap.get(id);
            if(task.getAction() == ACTION_UNKNOWN || task.getAction() == ACTION_REST) {
                agents.add(worldInfo.getEntity(id));
            }
        }
        for(EntityID id : this.request) {
            if(agents.isEmpty()) {
                return;
            }
            StandardEntity entity = worldInfo.getEntity(id);
            if(entity instanceof Road) {
                agents.sort(new DistanceSorter(worldInfo, entity));
                messageManager.addMessage(new CommandPolice(
                        true,
                        agents.get(0).getID(),
                        entity.getID(),
                        CommandPolice.ACTION_CLEAR
                ));
            } else if(entity.getStandardURN() == StandardEntityURN.BLOCKADE) {
                entity = worldInfo.getEntity(((Blockade)entity).getPosition());
                agents.sort(new DistanceSorter(worldInfo, entity));
                messageManager.addMessage(new CommandPolice(
                        true,
                        agents.get(0).getID(),
                        entity.getID(),
                        CommandPolice.ACTION_CLEAR
                ));
            }
        }

    }

    private void updateAgentTaskInfo(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager) {
        Collection<EntityID> policeIDs = worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_FORCE);
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessagePoliceForce.class)) {
            MessagePoliceForce mpf = (MessagePoliceForce) message;
            if (mpf.getSenderID().getValue() == mpf.getAgentID().getValue()) {
                this.agentTaskMap.put(
                        mpf.getAgentID(),
                        new Task(agentInfo.getTime(), mpf.getAction(), mpf.getTargetID())
                );
                if(mpf.getAction() == ACTION_CLEAR) {
                    this.request.remove(mpf.getTargetID());
                } else if(mpf.getAction() == ACTION_MOVE) {
                    this.request.remove(mpf.getTargetID());
                }
            }
        }
        /*for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageReport.class)) {
            MessageReport mr = (MessageReport) message;
            if (policeIDs.contains(mr.getSenderID())) {
            }
        }*/
    }

    private void collectTask(WorldInfo worldInfo, MessageManager messageManager) {
        Collection<EntityID> policeIDs = worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_FORCE);
        Collection<EntityID> commanders = worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_OFFICE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == CommandPolice.class) {
                CommandPolice command = (CommandPolice)message;
                if(!commanders.contains(command.getSenderID())) {
                    if(command.getAction() == CommandPolice.ACTION_CLEAR) {
                        this.request.add(command.getTargetID());
                    }
                }
            } else if(messageClass == MessageRoad.class) {
                MessageRoad mr = (MessageRoad)message;
                if(!mr.isPassable()) {
                    if(mr.isBlockadeDefined()) {
                        this.request.add(mr.getRoadID());
                    }
                }
            }
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeInfo, DevelopData debugData) {

    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData) {

    }

    private class Task {
        private int time;
        private int action;
        private EntityID target;

        Task(int time, int action, EntityID target) {
            this.time = time;
            this.action = action;
            this.target = target;
        }

        int getStartTime() {
            return this.time;
        }

        int getAction() {
            return this.action;
        }

        EntityID getTarget() {
            return this.target;
        }
    }

    private class DistanceSorter implements Comparator<StandardEntity> {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference) {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b) {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
