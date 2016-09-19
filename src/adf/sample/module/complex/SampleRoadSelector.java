package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadSelector;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.HYDRANT;
import static rescuecore2.standard.entities.StandardEntityURN.ROAD;

public class SampleRoadSelector extends RoadSelector {

    private Collection<EntityID> impassableArea;

    private EntityID result;

    public SampleRoadSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.result = null;
    }

    @Override
    public RoadSelector calc() {
        this.result = null;
        EntityID positionID = this.agentInfo.getPosition();
        if(this.impassableArea.contains(positionID)){
            this.result = positionID;
            return this;
        }
        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsPolice.RoadSelector");
        pathPlanning.setFrom(positionID);
        pathPlanning.setDestination(this.impassableArea);
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public RoadSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public RoadSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        this.impassableArea = this.worldInfo.getEntityIDsOfType(ROAD, HYDRANT);
        return this;
    }

    @Override
    public RoadSelector preparate() {
        super.preparate();
        this.impassableArea = this.worldInfo.getEntityIDsOfType(ROAD, HYDRANT);
        return this;
    }

    @Override
    public RoadSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(MessageRoad.class)) {
            MessageRoad messageRoad = (MessageRoad)message;
            if(messageRoad.isPassable()) {
                this.impassableArea.remove(messageRoad.getRoadID());
            }
        }
        for(EntityID id : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
            if(entity instanceof Road) {
                Road road = (Road)entity;
                if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                    if(this.impassableArea.remove(id)) {
                        messageManager.addMessage(new MessageRoad(true, road, null, true));
                    }
                }
            }
        }
        return this;
    }
}
