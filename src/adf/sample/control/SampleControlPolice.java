package adf.sample.control;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.control.ControlPolice;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleControlPolice extends ControlPolice {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = 0;
    private static final int ACTION_MOVE = 1;
    private static final int ACTION_CLEAR = 2;

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
        this.collectTask(agentInfo, worldInfo, messageManager);
    }

    private void collectTask(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager) {
        Collection<EntityID> policeIDs = worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_FORCE);
        Collection<EntityID> commanders = worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_OFFICE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce)message;
                if(mpf.getSenderID().getValue() == mpf.getAgentID().getValue()) {
                    this.agentTaskMap.put(
                            mpf.getAgentID(),
                            new Task(agentInfo.getTime(), mpf.getAction(), mpf.getTargetID())
                    );
                }
            } else if(messageClass == CommandPolice.class) {
                CommandPolice command = (CommandPolice)message;
                if(!commanders.contains(command.getSenderID())) {
                    if(command.getAction() == CommandPolice.ACTION_CLEAR) {
                        this.request.add(command.getTargetID());
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
}
