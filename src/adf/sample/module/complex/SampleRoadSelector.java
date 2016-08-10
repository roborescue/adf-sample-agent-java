package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.debug.DebugData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.RoadSelector;
import adf.sample.util.DistanceSorter;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleRoadSelector  extends RoadSelector {

    private Set<Road> impassableArea;

    private EntityID result;

    public SampleRoadSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DebugData debugData) {
        super(ai, wi, si, moduleManager, debugData);
        this.result = null;
        this.impassableArea = new HashSet<>();
    }

    @Override
    public RoadSelector calc() {
        this.result = null;
        Area area = this.agentInfo.getPositionArea();
        if(this.impassableArea.contains(area)){
            this.result = area.getID();
            return this;
        }
        Object[] roadArray = this.impassableArea.toArray();
        List<Road> impassableList = new ArrayList<>();
        for(Object obj : roadArray) {
            impassableList.add((Road)obj);
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
        for(StandardEntity entity : this.worldInfo.getEntitiesOfType(StandardEntityURN.ROAD, StandardEntityURN.HYDRANT)) {
            this.impassableArea.add((Road)entity);
        }
        return this;
    }

    @Override
    public RoadSelector preparate() {
        super.preparate();
        for(StandardEntity entity : this.worldInfo.getEntitiesOfType(StandardEntityURN.ROAD, StandardEntityURN.HYDRANT)) {
            this.impassableArea.add((Road)entity);
        }
        return this;
    }

    @Override
    public RoadSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        for(EntityID id : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
            if(entity instanceof Road) {
                Road road = (Road)entity;
                if(road.isBlockadesDefined() && road.getBlockades().isEmpty()) {
                    this.impassableArea.remove(road);
                    messageManager.addMessage(new MessageRoad(true, road, null, true));
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            if(message.getClass() == MessageRoad.class) {
                MessageRoad messageRoad = (MessageRoad)message;
                if(messageRoad.isPassable()) {
                    this.impassableArea.remove(this.worldInfo.getEntity(messageRoad.getRoadID()));
                }
            }
        }
        return this;
    }
}
