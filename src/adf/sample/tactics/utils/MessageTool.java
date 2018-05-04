package adf.sample.tactics.utils;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.StandardMessagePriority;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageBuilding;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.CommunicationMessage;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class MessageTool
{
    @SuppressWarnings("unused")
    private DevelopData developData;

    private int sendingAvoidTimeReceived;
    @SuppressWarnings("unused")
    private int sendingAvoidTimeSent;
    private int sendingAvoidTimeClearRequest;
    private int estimatedMoveDistance;

    private int maxTimeStep = Integer.MAX_VALUE;
    private Map<EntityID, Integer> prevBrokenessMap;
    private EntityID lastPosition;
    private int lastSentTime;
    private int stayCount;

    private Map<EntityID, Integer> receivedTimeMap;
    private Set<EntityID> agentsPotition;
    private Set<EntityID> receivedPassableRoads;

    private EntityID dominanceAgentID;

    public MessageTool(ScenarioInfo scenarioInfo, DevelopData developData)
    {
        this.developData = developData;

        this.sendingAvoidTimeReceived = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeReceived", 3);
        this.sendingAvoidTimeSent = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeSent", 5);
        this.sendingAvoidTimeClearRequest = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeClearRequest", 5);
        this.estimatedMoveDistance = developData.getInteger("sample.tactics.MessageTool.estimatedMoveDistance", 40000);

        this.lastPosition = new EntityID(0);
        this.lastSentTime = 0;
        this.stayCount = 0;

        this.prevBrokenessMap = new HashMap<>();
        this.receivedTimeMap = new HashMap<>();
        this.agentsPotition = new HashSet<>();
        this.receivedPassableRoads = new HashSet<>();

        this.dominanceAgentID = new EntityID(0);
    }

    public void reflectMessage(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        changedEntities.add(agentInfo.getID());
        int time = agentInfo.getTime();
        for (CommunicationMessage message : messageManager.getReceivedMessageList(StandardMessage.class))
        {
            StandardEntity entity = null;
            entity = MessageUtil.reflectMessage(worldInfo, (StandardMessage) message);
            if (entity != null) { this.receivedTimeMap.put(entity.getID(), time); }
        }
    }

    @SuppressWarnings("incomplete-switch")
    public void sendInformationMessages(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        this.updateInfo(agentInfo, worldInfo, scenarioInfo, messageManager);

        if (isPositionMoved(agentInfo) && isDominance(agentInfo))
        {
            for (EntityID entityID : changedEntities)
            {
                if (!(isRecentlyReceived(agentInfo, entityID)))
                {
                    StandardEntity entity = worldInfo.getEntity(entityID);
                    CommunicationMessage message = null;
                    switch (entity.getStandardURN())
                    {
                        case ROAD:
                            Road road = (Road) entity;
                            if (isNonBlockadeAndNotReceived(road))
                            {
                                message = new MessageRoad(true, StandardMessagePriority.LOW,
                                        road, null,
                                        true, false);
                            }
                            break;
                        case BUILDING:
                            Building building = (Building) entity;
                            if (isOnFireOrWaterDameged(building))
                            {
                                message = new MessageBuilding(true, StandardMessagePriority.LOW, building);
                            }
                            break;
                        case CIVILIAN:
                            Civilian civilian = (Civilian) entity;
                            if (isUnmovalCivilian(civilian))
                            {
                                message = new MessageCivilian(true, StandardMessagePriority.LOW, civilian);
                            }
                            break;
                    }

                    messageManager.addMessage(message);
                }
            }
        }

        recordLastPosition(agentInfo);
    }

    public void sendRequestMessages (AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        if (agentInfo.me().getStandardURN() == AMBULANCE_TEAM
                || agentInfo.me().getStandardURN() == FIRE_BRIGADE)
        {
            int currentTime = agentInfo.getTime();
            Human agent = (Human) agentInfo.me();
            int agentX = agent.getX();
            int agentY = agent.getY();
            StandardEntity positionEntity = worldInfo.getPosition(agent);
            if (positionEntity instanceof Road)
            {
                boolean isSendRequest = false;

                Road road = (Road) positionEntity;
                if (road.isBlockadesDefined() && road.getBlockades().size() > 0)
                {
                    for (Blockade blockade : worldInfo.getBlockades(road))
                    {
                        if (blockade == null || !blockade.isApexesDefined())
                        { continue; }

                        if (this.isInside(agentX, agentY, blockade.getApexes()))
                        { isSendRequest = true; }
                    }
                }

                if (this.lastPosition != null && this.lastPosition.getValue() == road.getID().getValue())
                {
                    this.stayCount++;
                    if (this.stayCount > this.getMaxTravelTime(road))
                    {
                        isSendRequest = true;
                    }
                }
                else
                {
                    this.lastPosition = road.getID();
                    this.stayCount = 0;
                }

                if (isSendRequest && ((currentTime - this.lastSentTime) >= this.sendingAvoidTimeClearRequest))
                {
                    this.lastSentTime = currentTime;
                    messageManager.addMessage(
                            new CommandPolice( true, null, agent.getPosition(), CommandPolice.ACTION_CLEAR )
                    );
                }
            }
        }
    }

    private void updateInfo(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager)
    {
        if (this.maxTimeStep == Integer.MAX_VALUE)
        {
            try
            {
                this.maxTimeStep = scenarioInfo.getKernelTimesteps();
            }
            catch (NoSuchConfigOptionException e)
            {}
        }

        this.agentsPotition.clear();
        this.dominanceAgentID = agentInfo.getID();

        for (StandardEntity entity : worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            Human human = (Human) entity;
            this.agentsPotition.add(human.getPosition());
            if (agentInfo.getPosition().equals(human.getPosition())
                    && dominanceAgentID.getValue() < entity.getID().getValue())
            {
                this.dominanceAgentID = entity.getID();
            }
        }

        boolean aftershock = false;
        for (EntityID id : agentInfo.getChanged().getChangedEntities())
        {
            if (this.prevBrokenessMap.containsKey(id) && worldInfo.getEntity(id).getStandardURN().equals(BUILDING))
            {
                Building building = (Building)worldInfo.getEntity(id);
                int brokenness = this.prevBrokenessMap.get(id);
                this.prevBrokenessMap.get(id);
                if (building.isBrokennessDefined())
                {
                    if (building.getBrokenness() > brokenness)
                    {
                        aftershock = true;
                    }
                }
            }
        }
        this.prevBrokenessMap.clear();
        for (EntityID id : agentInfo.getChanged().getChangedEntities())
        {
            if (! worldInfo.getEntity(id). getStandardURN().equals(BUILDING)) { continue; }

            Building building = (Building)worldInfo.getEntity(id);
            if (building.isBrokennessDefined())
            {
                this.prevBrokenessMap.put(id, building.getBrokenness());
            }
        }
        if (aftershock)
        {
            this.receivedPassableRoads.clear();
        }

        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageRoad.class))
        {
            MessageRoad messageRoad = (MessageRoad) message;
            Boolean passable = messageRoad.isPassable();
            if (passable != null && passable)
            {
                this.receivedPassableRoads.add(messageRoad.getRoadID());
            }
        }
    }

    private boolean isPositionMoved(AgentInfo agentInfo)
    {
        return !(agentInfo.getID().equals(lastPosition));
    }

    private boolean isDominance(AgentInfo agentInfo)
    {
        return agentInfo.getID().equals(this.dominanceAgentID);
    }

    private boolean isRecentlyReceived(AgentInfo agentInfo, EntityID id)
    {
        return (this.receivedTimeMap.containsKey(id)
                && ((agentInfo.getTime() - this.receivedTimeMap.get(id)) < this.sendingAvoidTimeReceived));
    }

    private boolean isNonBlockadeAndNotReceived(Road road)
    {
        if ((!road.isBlockadesDefined()) || (road.isBlockadesDefined() && (road.getBlockades().size() <= 0) ))
        {
            if (!(this.receivedPassableRoads.contains(road.getID())))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isOnFireOrWaterDameged(Building building)
    {
        final List<StandardEntityConstants.Fieryness> ignoreFieryness
                = Arrays.asList(StandardEntityConstants.Fieryness.UNBURNT, StandardEntityConstants.Fieryness.BURNT_OUT);

        if (building.isFierynessDefined() && ignoreFieryness.contains(building.getFierynessEnum()) )
        {
            return false;
        }

        return true;
    }

    private boolean isUnmovalCivilian(Civilian civilian)
    {
        return civilian.isDamageDefined() && (civilian.getDamage() > 0);
    }

    private void recordLastPosition(AgentInfo agentInfo)
    {
        this. lastPosition = agentInfo.getPosition();
    }

    private boolean isInside(double pX, double pY, int[] apex)
    {
        Point2D p = new Point2D(pX, pY);
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        double theta = this.getAngle(v1, v2);

        for (int i = 0; i < apex.length - 2; i += 2)
        {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }

    private double getAngle(Vector2D v1, Vector2D v2)
    {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if (flag > 0)
        {
            return angle;
        }
        if (flag < 0)
        {
            return -1 * angle;
        }
        return 0.0D;
    }

    private int getMaxTravelTime(Area area)
    {
        int distance = 0;
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : area.getEdges())
        {
            if (edge.isPassable())
            {
                edges.add(edge);
            }
        }
        if (edges.size() <= 1)
        {
            return this.maxTimeStep;
        }
        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = 0; j < edges.size(); j++)
            {
                if (i != j)
                {
                    Edge edge1 = edges.get(i);
                    double midX1 = (edge1.getStartX() + edge1.getEndX()) / 2;
                    double midY1 = (edge1.getStartY() + edge1.getEndY()) / 2;
                    Edge edge2 = edges.get(j);
                    double midX2 = (edge2.getStartX() + edge2.getEndX()) / 2;
                    double midY2 = (edge2.getStartY() + edge2.getEndY()) / 2;
                    int d = this.getDistance(midX1, midY1, midX2, midY2);
                    if (distance < d)
                    {
                        distance = d;
                    }
                }
            }
        }

        if (distance > 0)
        {
            return 1 + (int)Math.ceil( distance / (double)this.estimatedMoveDistance);
        }

        return this.maxTimeStep;
    }

    private int getDistance(double fromX, double fromY, double toX, double toY)
    {
        double dx = toX - fromX;
        double dy = toY - fromY;
        return (int) Math.hypot(dx, dy);
    }
}
