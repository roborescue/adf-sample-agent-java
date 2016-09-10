package adf.sample.module.complex.topdown;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.communication.standard.bundle.topdown.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import com.google.common.collect.Lists;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleTaskSearch extends Search {

    private EntityID result;
    private SearchTask task;

    public SampleTaskSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.result = null;
        this.task = null;
    }

    @Override
    public Search calc() {
        this.result = null;
        if(this.task == null || this.task.getClass() == OtherTask.class) {
            return this;
        }

        PathPlanning pathPlanning = null;
        if(this.agentInfo.me().getStandardURN() == AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule("TacticsAmbulance.PathPlanning");
        } else if(this.agentInfo.me().getStandardURN() == FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");
        } else if(this.agentInfo.me().getStandardURN() == POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
        }
        if(pathPlanning == null) {
            return this;
        }

        pathPlanning.setFrom(this.agentInfo.getPosition());
        pathPlanning.setDestination(this.task.getTargetIDs());
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        this.reflectMessage(messageManager);
        this.sendMessage(messageManager);
        this.updateTask(messageManager);
        this.sendClearRequest(messageManager);
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void updateTask(MessageManager messageManager) {
        if(this.task != null) {
            this.task.updateInfo(this.agentInfo, this.worldInfo);
            if(this.task.isCompleted()) {
                messageManager.addMessage(new MessageReport(true, true, false, this.task.getSenderID()));
                this.task = null;
            }
        }
        SearchTask searchTask = this.getScoutTask(messageManager);
        if(searchTask != null) {
            this.task = searchTask;
        }

        SearchTask agentTask = null;
        StandardEntityURN agentURN = this.agentInfo.me().getStandardURN();
        if(agentURN == AMBULANCE_TEAM) {
            agentTask = this.getAmbulanceTask(messageManager);
        } else if(agentURN == FIRE_BRIGADE) {
            agentTask = this.getFireTask(messageManager);
        } else if(agentURN == POLICE_FORCE) {
            agentTask = this.getPoliceTask(messageManager);
        }
        if(agentTask != null) {
            if(agentTask.getClass() == RestTask.class) {
                if(searchTask == null) {
                    this.task = agentTask;
                }
            } else {
                this.task = agentTask;
            }
        }
    }

    private void sendClearRequest(MessageManager messageManager) {
        StandardEntity entity = this.agentInfo.me();
        if(!(entity instanceof Human)) {
            return;
        }
        if(entity.getStandardURN() == POLICE_FORCE) {
            return;
        }
        Human currentAgent = (Human)entity;
        StandardEntity position = this.worldInfo.getPosition(currentAgent);
        if(position instanceof Road) {
            for (EntityID blockadeID : ((Road) position).getBlockades()) {
                if (this.isInside(
                        currentAgent.getX(),
                        currentAgent.getY(),
                        ((Blockade) this.worldInfo.getEntity(blockadeID)).getApexes()
                )) {
                    messageManager.addMessage(new CommandPolice(
                            true,
                            null,
                            position.getID(),
                            CommandPolice.ACTION_CLEAR
                    ));
                    return;
                }
            }
        }
    }

    private SearchTask getScoutTask(MessageManager messageManager) {
        StandardEntity me = this.agentInfo.me();
        StandardEntityURN agentURN = me.getStandardURN();
        Collection<EntityID> commanders = new HashSet<>();
        if(agentURN == AMBULANCE_TEAM) {
            commanders = this.worldInfo.getEntityIDsOfType(AMBULANCE_CENTRE);
        } else if(agentURN == FIRE_BRIGADE) {
            commanders = this.worldInfo.getEntityIDsOfType(FIRE_STATION);
        } else if(agentURN == POLICE_FORCE) {
            commanders = this.worldInfo.getEntityIDsOfType(POLICE_OFFICE);
        }

        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(commanders.contains(command.getSenderID())) {
                if(command.getToID().getValue() == me.getID().getValue()) {
                    Collection<EntityID> scoutTargetIDs = new HashSet<>();
                    StandardEntity center = worldInfo.getEntity(command.getTargetID());
                    for(StandardEntity entity : worldInfo.getObjectsInRange(center, command.getRange())) {
                        if(entity instanceof Area && entity.getStandardURN() != REFUGE) {
                            scoutTargetIDs.add(entity.getID());
                        }
                    }
                    return new ScoutTask(command.getSenderID(), scoutTargetIDs);
                }
            }
        }
        return null;
    }

    private SearchTask getAmbulanceTask(MessageManager messageManager) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(AMBULANCE_CENTRE);
        SearchTask result = null;
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance)message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            if(command.getAction() == CommandAmbulance.ACTION_MOVE) {
                return new MoveTask(command.getSenderID(), command.getTargetID());
            } else if(command.getAction() == CommandAmbulance.ACTION_REST) {
                EntityID target = command.getTargetID();
                if(target != null) {
                    return new RestTask(command.getSenderID(), Lists.newArrayList(target));
                } else {
                    return new RestTask(command.getSenderID(), this.worldInfo.getEntityIDsOfType(REFUGE));
                }
            } else {
                result = new OtherTask(command.getSenderID(), command.getTargetID());
            }
        }
        return result;
    }

    private SearchTask getFireTask(MessageManager messageManager) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(FIRE_STATION);
        SearchTask result = null;
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire)message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            if(command.getAction() == CommandFire.ACTION_MOVE) {
                return new MoveTask(command.getSenderID(), command.getTargetID());
            } else if(command.getAction() == CommandFire.ACTION_REST) {
                EntityID target = command.getTargetID();
                if(target != null) {
                    return new RestTask(command.getSenderID(), Lists.newArrayList(target));
                } else {
                    return new RestTask(command.getSenderID(), this.worldInfo.getEntityIDsOfType(REFUGE));
                }
            } else {
                result = new OtherTask(command.getSenderID(), command.getTargetID());
            }
        }
        return result;
    }

    private SearchTask getPoliceTask(MessageManager messageManager) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(POLICE_OFFICE);
        SearchTask result = null;
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice)message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            if(command.getAction() == CommandFire.ACTION_MOVE) {
                return new MoveTask(command.getSenderID(), command.getTargetID());
            } else if(command.getAction() == CommandFire.ACTION_REST) {
                EntityID target = command.getTargetID();
                if(target != null) {
                    return new RestTask(command.getSenderID(), Lists.newArrayList(target));
                } else {
                    return new RestTask(command.getSenderID(), this.worldInfo.getEntityIDsOfType(REFUGE));
                }
            } else {
                result = new OtherTask(command.getSenderID(), command.getTargetID());
            }
        }
        return result;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageBuilding.class) {
                MessageBuilding mb = (MessageBuilding)message;
                if(!changedEntities.contains(mb.getBuildingID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mb);
                }
            } else if(messageClass == MessageRoad.class) {
                MessageRoad mr = (MessageRoad)message;
                if(mr.isBlockadeDefined() && !changedEntities.contains(mr.getBlockadeID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mr);
                }
            } else if(messageClass == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!changedEntities.contains(mc.getAgentID())){
                    MessageUtil.reflectMessage(this.worldInfo, mc);
                }
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mat);
                }
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!changedEntities.contains(mfb.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mfb);
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mpf);
                }
            }
        }
    }

    private void sendMessage(MessageManager messageManager) {
        for(EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(id);
            if(entity instanceof Building) {
                Building building = (Building)entity;
                if(building.isFierynessDefined() && building.isOnFire()) {
                    messageManager.addMessage(new MessageBuilding(true, building));
                }
            } else if(entity instanceof Road) {
                Road road = (Road)entity;
                List<EntityID> blockades = road.getBlockades();
                if(blockades == null || blockades.isEmpty()) {
                    messageManager.addMessage(new MessageRoad(true, road, null, true));
                }
            } else if(entity.getStandardURN() == CIVILIAN) {
                Civilian civilian = (Civilian)entity;
                if(this.needSend(civilian)) {
                    messageManager.addMessage(new MessageCivilian(true, civilian));
                }
            } else if(entity.getStandardURN() == BLOCKADE) {
                Blockade blockade = (Blockade)entity;
                Road road = (Road)this.worldInfo.getEntity(blockade.getPosition());
                StandardEntity rollbackEntity = this.worldInfo.getEntity(-1, id);
                if(rollbackEntity == null) {
                    messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                } else {
                    Blockade rollbackBlockade = (Blockade) rollbackEntity;
                    if (blockade.getRepairCost() != rollbackBlockade.getRepairCost()) {
                        messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                    }
                }
            }
        }
    }

    private boolean needSend(Civilian civilian) {
        if(civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
            return true;
        }
        else if(civilian.isDamageDefined() && civilian.getDamage() > 0
                && civilian.isPositionDefined() && this.worldInfo.getEntity(civilian.getPosition()) instanceof Road){
            return true;
        }
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public Search precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public Search preparate() {
        super.preparate();
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean isInside(double pX, double pY, int[] apex) {
        Point2D p = new Point2D(pX, pY);
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        double theta = this.getAngle(v1, v2);

        for(int i = 0; i < apex.length - 2; i += 2) {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }

    private double getAngle(Vector2D v1, Vector2D v2) {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if(flag > 0) {
            return angle;
        }
        if(flag < 0) {
            return -1 * angle;
        }
        return 0.0D;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private abstract class SearchTask {
        private EntityID senderID;

        SearchTask(EntityID senderID) {
            this.senderID = senderID;
        }

        abstract SearchTask updateInfo(AgentInfo agentInfo, WorldInfo worldInfo);
        abstract boolean isCompleted();
        abstract Collection<EntityID> getTargetIDs();

        EntityID getSenderID() {
            return this.senderID;
        }

    }

    private class ScoutTask extends SearchTask {
        private Collection<EntityID> scoutTargetIDs;

        ScoutTask(EntityID senderID, Collection<EntityID> targetEntities) {
            super(senderID);
            this.scoutTargetIDs = targetEntities;
        }

        @Override
        public ScoutTask updateInfo(AgentInfo agentInfo, WorldInfo worldInfo) {
            if(this.scoutTargetIDs == null) {
                this.scoutTargetIDs = new HashSet<>();
            }
            this.scoutTargetIDs.removeAll(worldInfo.getChanged().getChangedEntities());
            return this;
        }

        @Override
        public boolean isCompleted() {
            return this.scoutTargetIDs.isEmpty();
        }

        @Override
        public Collection<EntityID> getTargetIDs() {
            return this.scoutTargetIDs;
        }
    }

    private class MoveTask extends SearchTask {
        private EntityID moveTargetID;
        private boolean isCompleted;

        MoveTask(EntityID senderID, EntityID target) {
            super(senderID);
            this.moveTargetID = target;
            this.isCompleted = false;
        }

        @Override
        public MoveTask updateInfo(AgentInfo agentInfo, WorldInfo worldInfo) {
            if(agentInfo.getPosition().getValue() == this.moveTargetID.getValue()) {
                this.isCompleted = true;
            }
            return this;
        }

        @Override
        public boolean isCompleted() {
            return this.isCompleted;
        }

        public Collection<EntityID> getTargetIDs() {
            return Lists.newArrayList(this.moveTargetID);
        }
    }

    private class RestTask extends SearchTask {
        private Collection<EntityID> restPosition;

        RestTask(EntityID senderID, Collection<EntityID> rest) {
            super(senderID);
            this.restPosition = rest;
        }

        @Override
        public RestTask updateInfo(AgentInfo agentInfo, WorldInfo worldInfo) {
            return this;
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Collection<EntityID> getTargetIDs() {
            return this.restPosition;
        }
    }

    private class OtherTask extends SearchTask {
        private EntityID targetID;
        OtherTask(EntityID senderID, EntityID targetID) {
            super(senderID);
            this.targetID = targetID;
        }

        @Override
        public OtherTask updateInfo(AgentInfo agentInfo, WorldInfo worldInfo) {
            return this;
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Collection<EntityID> getTargetIDs() {
            return Lists.newArrayList(this.targetID);
        }
    }
}