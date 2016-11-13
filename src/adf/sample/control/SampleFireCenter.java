package adf.sample.control;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.topdown.CommandFire;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.tactics.center.TacticsFireCenter;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleFireCenter extends TacticsFireCenter {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandFire.ACTION_REST;
    private static final int ACTION_MOVE = CommandFire.ACTION_MOVE;
    private static final int ACTION_EXTINGUISH = CommandFire.ACTION_EXTINGUISH;
    private static final int ACTION_REFILL = CommandFire.ACTION_REFILL;

    private int thresholdCompleted;

    private List<Request> extinguishBuildingIDs;
    private Map<EntityID, Command> commandMap;
    private int resetTime;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.extinguishBuildingIDs = new ArrayList<>();
        this.commandMap = new HashMap<>();
        this.resetTime = developData.getInteger("sample.control.SampleFireCenter.resetTime", 5);
        int maxWater = scenarioInfo.getFireTankMaximum();
        this.thresholdCompleted = (maxWater / 10) * developData.getInteger("sample.control.SampleFireCenter.refill", 7);
        for(EntityID id : worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_BRIGADE)) {
            commandMap.put(id, new Command(0, ACTION_UNKNOWN, null));
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeInfo, DevelopData developData) {

    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData) {

    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        int currentTime = agentInfo.getTime();
        // task check
        for(CommunicationMessage message : messageManager.getReceivedMessageList(MessageFireBrigade.class)) {
            MessageFireBrigade mfb = (MessageFireBrigade)message;
            if(mfb.getSenderID().getValue() == mfb.getAgentID().getValue()) {
                MessageUtil.reflectMessage(worldInfo, mfb);
                this.commandMap.put(mfb.getAgentID(), new Command(currentTime, mfb.getAction(), mfb.getTargetID()));
            }
        }
        for(EntityID id : this.commandMap.keySet()) {
            Command command = this.commandMap.get(id);
            if(command.getTime() + this.resetTime <= currentTime) {
                commandMap.put(id, new Command(currentTime, ACTION_UNKNOWN, null));
            }
        }
        // request check
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire)message;
            if(command.getAction() == CommandFire.ACTION_EXTINGUISH) {
                this.extinguishBuildingIDs.add(new Request(currentTime, command.getTargetID()));
            }
        }
        List<Request> requestList = new ArrayList<>();
        for(Request request : this.extinguishBuildingIDs) {
            if(request.getTime() + this.resetTime > currentTime) {
                requestList.add(request);
            }
        }
        this.extinguishBuildingIDs.clear();
        this.extinguishBuildingIDs.addAll(requestList);
        // send Command
        List<StandardEntity> commandAgents = new ArrayList<>();
        for(EntityID id : this.commandMap.keySet()) {
            Command command = this.commandMap.get(id);
            FireBrigade fire = (FireBrigade)worldInfo.getEntity(id);
            if(command.getAction() == ACTION_REST) {
                if(fire.getWater() >= this.thresholdCompleted) {
                    commandAgents.add(fire);
                }
            } else if(command.getAction() == ACTION_MOVE) {
                StandardEntity entity = worldInfo.getEntity(command.getTarget());
                if(entity.getStandardURN() == StandardEntityURN.ROAD) {
                    commandAgents.add(fire);
                }
            } else if(command.getAction() == ACTION_REFILL) {
                if(fire.getWater() >= this.thresholdCompleted) {
                    commandAgents.add(fire);
                }
            }
        }
        while (this.extinguishBuildingIDs.size() > 0 && commandAgents.size() > 0) {
            StandardEntity entity = worldInfo.getEntity(this.extinguishBuildingIDs.get(0).getTarget());
            if(entity instanceof Building) {
                commandAgents.sort(new DistanceSorter(worldInfo, entity));
                messageManager.addMessage(new CommandFire(
                        true,
                        commandAgents.get(0).getID(),
                        entity.getID(),
                        ACTION_EXTINGUISH
                ));
                commandAgents.remove(0);
            }
            this.extinguishBuildingIDs.remove(0);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private class Command {
        private int time;
        private int action;
        private EntityID target;

        Command(int time, int action, EntityID target) {
            this.time = time;
            this.action = action;
            this.target = target;
        }

        int getTime() {
            return this.time;
        }

        int getAction() {
            return this.action;
        }

        EntityID getTarget() {
            return this.target;
        }
    }

    private class Request {
        private EntityID target;
        private int time;

        Request(int time, EntityID target) {
            this.target = target;
            this.time = time;
        }

        int getTime() {
            return this.time;
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
