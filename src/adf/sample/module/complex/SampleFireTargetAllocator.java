package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.FireTargetAllocator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleFireTargetAllocator extends FireTargetAllocator {

  private Collection<EntityID>           priorityHumans;
  private Collection<EntityID>           targetHumans;

  private Map<EntityID, FireBrigadeInfo> fireBrigadeInfoMap;


  public SampleFireTargetAllocator( AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData ) {
    super( ai, wi, si, moduleManager, developData );
    this.priorityHumans = new HashSet<>();
    this.targetHumans = new HashSet<>();
    this.fireBrigadeInfoMap = new HashMap<>();
  }


  @Override
  public FireTargetAllocator resume( PrecomputeData precomputeData ) {
    super.resume( precomputeData );
    if ( this.getCountResume() >= 2 ) {
      return this;
    }
    for ( EntityID id : this.worldInfo
        .getEntityIDsOfType( StandardEntityURN.FIRE_BRIGADE ) ) {
      this.fireBrigadeInfoMap.put( id, new FireBrigadeInfo( id ) );
    }
    return this;
  }


  @Override
  public FireTargetAllocator preparate() {
    super.preparate();
    if ( this.getCountPrecompute() >= 2 ) {
      return this;
    }
    for ( EntityID id : this.worldInfo
        .getEntityIDsOfType( StandardEntityURN.AMBULANCE_TEAM ) ) {
      this.fireBrigadeInfoMap.put( id, new FireBrigadeInfo( id ) );
    }
    return this;
  }


  @Override
  public Map<EntityID, EntityID> getResult() {
    return this.convert( this.fireBrigadeInfoMap );
  }


  @Override
  public FireTargetAllocator calc() {
    List<StandardEntity> agents = this
        .getActionAgents( this.fireBrigadeInfoMap );
    Collection<EntityID> removes = new ArrayList<>();
    int currentTime = this.agentInfo.getTime();
    for ( EntityID target : this.priorityHumans ) {
      if ( agents.size() > 0 ) {
        StandardEntity targetEntity = this.worldInfo.getEntity( target );
        if ( targetEntity != null && targetEntity instanceof Human
            && ( (Human) targetEntity ).isPositionDefined() ) {
          agents.sort( new DistanceSorter( this.worldInfo, targetEntity ) );
          StandardEntity result = agents.get( 0 );
          agents.remove( 0 );
          FireBrigadeInfo info = this.fireBrigadeInfoMap.get( result.getID() );
          if ( info != null ) {
            info.canNewAction = false;
            info.target = target;
            info.commandTime = currentTime;
            this.fireBrigadeInfoMap.put( result.getID(), info );
            removes.add( target );
          }
        }
      }
    }
    this.priorityHumans.removeAll( removes );
    removes.clear();
    for ( EntityID target : this.targetHumans ) {
      if ( agents.size() > 0 ) {
        StandardEntity targetEntity = this.worldInfo.getEntity( target );
        if ( targetEntity != null && targetEntity instanceof Human
            && ( (Human) targetEntity ).isPositionDefined() ) {
          agents.sort( new DistanceSorter( this.worldInfo, targetEntity ) );
          StandardEntity result = agents.get( 0 );
          agents.remove( 0 );
          FireBrigadeInfo info = this.fireBrigadeInfoMap.get( result.getID() );
          if ( info != null ) {
            info.canNewAction = false;
            info.target = target;
            info.commandTime = currentTime;
            this.fireBrigadeInfoMap.put( result.getID(), info );
            removes.add( target );
          }
        }
      }
    }
    this.targetHumans.removeAll( removes );
    return this;
  }


  @Override
  public FireTargetAllocator updateInfo( MessageManager messageManager ) {
    super.updateInfo( messageManager );
    if ( this.getCountUpdateInfo() >= 2 ) {
      return this;
    }
    int currentTime = this.agentInfo.getTime();
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList() ) {
      Class<? extends CommunicationMessage> messageClass = message.getClass();
      if ( messageClass == MessageCivilian.class ) {
        MessageCivilian mc = (MessageCivilian) message;
        MessageUtil.reflectMessage( this.worldInfo, mc );
        if ( mc.isBuriednessDefined() && mc.getBuriedness() > 0 ) {
          this.targetHumans.add( mc.getAgentID() );
        } else {
          this.priorityHumans.remove( mc.getAgentID() );
          this.targetHumans.remove( mc.getAgentID() );
        }
      } else if ( messageClass == MessageFireBrigade.class ) {
        MessageFireBrigade mfb = (MessageFireBrigade) message;
        MessageUtil.reflectMessage( this.worldInfo, mfb );
        if ( mfb.isBuriednessDefined() && mfb.getBuriedness() > 0 ) {
          this.priorityHumans.add( mfb.getAgentID() );
        } else {
          this.priorityHumans.remove( mfb.getAgentID() );
          this.targetHumans.remove( mfb.getAgentID() );
        }
      } else if ( messageClass == MessagePoliceForce.class ) {
        MessagePoliceForce mpf = (MessagePoliceForce) message;
        MessageUtil.reflectMessage( this.worldInfo, mpf );
        if ( mpf.isBuriednessDefined() && mpf.getBuriedness() > 0 ) {
          this.priorityHumans.add( mpf.getAgentID() );
        } else {
          this.priorityHumans.remove( mpf.getAgentID() );
          this.targetHumans.remove( mpf.getAgentID() );
        }
      }
    }
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList( MessageFireBrigade.class ) ) {
      MessageFireBrigade mat = (MessageFireBrigade) message;
      MessageUtil.reflectMessage( this.worldInfo, mat );
      if ( mat.isBuriednessDefined() && mat.getBuriedness() > 0 ) {
        this.priorityHumans.add( mat.getAgentID() );
      } else {
        this.priorityHumans.remove( mat.getAgentID() );
        this.targetHumans.remove( mat.getAgentID() );
      }
      FireBrigadeInfo info = this.fireBrigadeInfoMap.get( mat.getAgentID() );
      if ( info == null ) {
        info = new FireBrigadeInfo( mat.getAgentID() );
      }
      if ( currentTime >= info.commandTime + 2 ) {
        this.fireBrigadeInfoMap.put( mat.getAgentID(),
            this.update( info, mat ) );
      }
    }
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList( CommandFire.class ) ) {
      CommandFire command = (CommandFire) message;
      if ( command.getAction() == CommandFire.ACTION_RESCUE
          && command.isBroadcast() ) {
        this.priorityHumans.add( command.getTargetID() );
        this.targetHumans.add( command.getTargetID() );
      }
    }
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList( MessageReport.class ) ) {
      MessageReport report = (MessageReport) message;
      FireBrigadeInfo info = this.fireBrigadeInfoMap
          .get( report.getSenderID() );
      if ( info != null && report.isDone() ) {
        info.canNewAction = true;
        this.priorityHumans.remove( info.target );
        this.targetHumans.remove( info.target );
        info.target = null;
        this.fireBrigadeInfoMap.put( info.agentID, info );
      }
    }
    return this;
  }


  private Map<EntityID, EntityID>
      convert( Map<EntityID, FireBrigadeInfo> map ) {
    Map<EntityID, EntityID> result = new HashMap<>();
    for ( EntityID id : map.keySet() ) {
      FireBrigadeInfo info = map.get( id );
      if ( info != null && info.target != null ) {
        result.put( id, info.target );
      }
    }
    return result;
  }


  private List<StandardEntity>
      getActionAgents( Map<EntityID, FireBrigadeInfo> map ) {
    List<StandardEntity> result = new ArrayList<>();
    for ( StandardEntity entity : this.worldInfo
        .getEntitiesOfType( StandardEntityURN.POLICE_FORCE ) ) {
      FireBrigadeInfo info = map.get( entity.getID() );
      if ( info != null && info.canNewAction
          && ( (FireBrigade) entity ).isPositionDefined() ) {
        result.add( entity );
      }
    }
    return result;
  }


  private FireBrigadeInfo update( FireBrigadeInfo info,
      MessageFireBrigade message ) {
    if ( message.isBuriednessDefined() && message.getBuriedness() > 0 ) {
      info.canNewAction = false;
      if ( info.target != null ) {
        this.targetHumans.add( info.target );
        info.target = null;
      }
      return info;
    }
    if ( message.getAction() == MessageFireBrigade.ACTION_REST ) {
      info.canNewAction = true;
      if ( info.target != null ) {
        this.targetHumans.add( info.target );
        info.target = null;
      }
    } else if ( message.getAction() == MessageFireBrigade.ACTION_MOVE ) {
      if ( message.getTargetID() != null ) {
        StandardEntity entity = this.worldInfo
            .getEntity( message.getTargetID() );
        if ( entity != null ) {
          if ( entity instanceof Area ) {
            if ( entity.getStandardURN() == REFUGE ) {
              info.canNewAction = false;
              return info;
            }
            StandardEntity targetEntity = this.worldInfo
                .getEntity( info.target );
            if ( targetEntity != null ) {
              if ( targetEntity instanceof Human ) {
                targetEntity = this.worldInfo
                    .getPosition( (Human) targetEntity );
                if ( targetEntity == null ) {
                  this.priorityHumans.remove( info.target );
                  this.targetHumans.remove( info.target );
                  info.canNewAction = true;
                  info.target = null;
                  return info;
                }
              }
              if ( targetEntity.getID().getValue() == entity.getID()
                  .getValue() ) {
                info.canNewAction = false;
              } else {
                info.canNewAction = true;
                if ( info.target != null ) {
                  this.targetHumans.add( info.target );
                  info.target = null;
                }
              }
            } else {
              info.canNewAction = true;
              info.target = null;
            }
            return info;
          } else if ( entity instanceof Human ) {
            if ( entity.getID().getValue() == info.target.getValue() ) {
              info.canNewAction = false;
            } else {
              info.canNewAction = true;
              this.targetHumans.add( info.target );
              this.targetHumans.add( entity.getID() );
              info.target = null;
            }
            return info;
          }
        }
      }
      info.canNewAction = true;
      if ( info.target != null ) {
        this.targetHumans.add( info.target );
        info.target = null;
      }
    } else if ( message.getAction() == MessageFireBrigade.ACTION_RESCUE ) {
      info.canNewAction = true;
      if ( info.target != null ) {
        this.targetHumans.add( info.target );
        info.target = null;
      }
    }
    return info;
  }


  private class FireBrigadeInfo {

    EntityID agentID;
    EntityID target;
    boolean  canNewAction;
    int      commandTime;


    FireBrigadeInfo( EntityID id ) {
      agentID = id;
      target = null;
      canNewAction = true;
      commandTime = -1;
    }
  }

  private class DistanceSorter implements Comparator<StandardEntity> {

    private StandardEntity reference;
    private WorldInfo      worldInfo;


    DistanceSorter( WorldInfo wi, StandardEntity reference ) {
      this.reference = reference;
      this.worldInfo = wi;
    }


    public int compare( StandardEntity a, StandardEntity b ) {
      int d1 = this.worldInfo.getDistance( this.reference, a );
      int d2 = this.worldInfo.getDistance( this.reference, b );
      return d1 - d2;
    }
  }
}