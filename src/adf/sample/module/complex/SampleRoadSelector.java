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
import adf.component.module.complex.RoadSelector;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
        List<StandardEntity> impassableList = new ArrayList<>();
        for(EntityID id : this.impassableArea) {
            impassableList.add(this.worldInfo.getEntity(id));
        }
        impassableList.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
        this.result = impassableList.get(0).getID();
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
        for(EntityID id : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
            if(entity instanceof Road) {
                Road road = (Road)entity;
                if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                    this.impassableArea.remove(id);
                    messageManager.addMessage(new MessageRoad(true, road, null, true));
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            if(message.getClass() == MessageRoad.class) {
                MessageRoad messageRoad = (MessageRoad)message;
                if(messageRoad.isPassable()) {
                    this.impassableArea.remove(messageRoad.getRoadID());
                }
            }
        }
        return this;
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
