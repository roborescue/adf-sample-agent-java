package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import java.util.ArrayList;
import java.util.List;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;

public class ActionFireRescue extends ExtAction {

  private PathPlanning pathPlanning;

  private int          thresholdRest;
  private int          kernelTime;

  private EntityID     target;


  public ActionFireRescue( AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData ) {
    super( agentInfo, worldInfo, scenarioInfo, moduleManager, developData );
    this.target = null;
    this.thresholdRest = developData.getInteger( "ActionFireRescue.rest", 100 );

    switch ( scenarioInfo.getMode() ) {
      case PRECOMPUTATION_PHASE:
        this.pathPlanning = moduleManager.getModule(
            "ActionFireRescue.PathPlanning",
            "adf.sample.module.algorithm.SamplePathPlanning" );
        break;
      case PRECOMPUTED:
        this.pathPlanning = moduleManager.getModule(
            "ActionFireRescue.PathPlanning",
            "adf.sample.module.algorithm.SamplePathPlanning" );
        break;
      case NON_PRECOMPUTE:
        this.pathPlanning = moduleManager.getModule(
            "ActionFireRescue.PathPlanning",
            "adf.sample.module.algorithm.SamplePathPlanning" );
        break;
    }
  }


  public ExtAction precompute( PrecomputeData precomputeData ) {
    super.precompute( precomputeData );
    if ( this.getCountPrecompute() >= 2 ) {
      return this;
    }
    this.pathPlanning.precompute( precomputeData );
    try {
      this.kernelTime = this.scenarioInfo.getKernelTimesteps();
    } catch ( NoSuchConfigOptionException e ) {
      this.kernelTime = -1;
    }
    return this;
  }


  public ExtAction resume( PrecomputeData precomputeData ) {
    super.resume( precomputeData );
    if ( this.getCountResume() >= 2 ) {
      return this;
    }
    this.pathPlanning.resume( precomputeData );
    try {
      this.kernelTime = this.scenarioInfo.getKernelTimesteps();
    } catch ( NoSuchConfigOptionException e ) {
      this.kernelTime = -1;
    }
    return this;
  }


  public ExtAction preparate() {
    super.preparate();
    if ( this.getCountPreparate() >= 2 ) {
      return this;
    }
    this.pathPlanning.preparate();
    try {
      this.kernelTime = this.scenarioInfo.getKernelTimesteps();
    } catch ( NoSuchConfigOptionException e ) {
      this.kernelTime = -1;
    }
    return this;
  }


  public ExtAction updateInfo( MessageManager messageManager ) {
    super.updateInfo( messageManager );
    if ( this.getCountUpdateInfo() >= 2 ) {
      return this;
    }
    this.pathPlanning.updateInfo( messageManager );
    return this;
  }


  @Override
  public ExtAction setTarget( EntityID target ) {
    this.target = null;
    if ( target != null ) {
      StandardEntity entity = this.worldInfo.getEntity( target );
      if ( entity instanceof Human || entity instanceof Area ) {
        this.target = target;
        return this;
      }
    }
    return this;
  }


  @Override
  public ExtAction calc() {
    this.result = null;
    FireBrigade agent = (FireBrigade) this.agentInfo.me();

    if ( this.needRest( agent ) ) {
      EntityID areaID = this.convertArea( this.target );
      ArrayList<EntityID> targets = new ArrayList<>();
      if ( areaID != null ) {
        targets.add( areaID );
      }
    }
    if ( this.target != null ) {
      this.result = this.calcRescue( agent, this.pathPlanning, this.target );
    }
    return this;
  }


  private Action calcRescue( FireBrigade agent, PathPlanning pathPlanning,
      EntityID targetID ) {
    StandardEntity targetEntity = this.worldInfo.getEntity( targetID );
    if ( targetEntity == null ) {
      return null;
    }
    EntityID agentPosition = agent.getPosition();
    if ( targetEntity instanceof Human ) {
      Human human = (Human) targetEntity;
      if ( !human.isPositionDefined() ) {
        return null;
      }
      if ( human.isHPDefined() && human.getHP() == 0 ) {
        return null;
      }
      EntityID targetPosition = human.getPosition();
      if ( agentPosition.getValue() == targetPosition.getValue() ) {
        if ( human.isBuriednessDefined() && human.getBuriedness() > 0 ) {
          return new ActionRescue( human );
        }
      } else {
        List<EntityID> path = pathPlanning.getResult( agentPosition,
            targetPosition );
        if ( path != null && path.size() > 0 ) {
          return new ActionMove( path );
        }
      }
      return null;
    }
    if ( targetEntity.getStandardURN() == BLOCKADE ) {
      Blockade blockade = (Blockade) targetEntity;
      if ( blockade.isPositionDefined() ) {
        targetEntity = this.worldInfo.getEntity( blockade.getPosition() );
      }
    }
    if ( targetEntity instanceof Area ) {
      List<EntityID> path = pathPlanning.getResult( agentPosition,
          targetEntity.getID() );
      if ( path != null && path.size() > 0 ) {
        return new ActionMove( path );
      }
    }
    return null;
  }


  private boolean needRest( Human agent ) {
    int hp = agent.getHP();
    int damage = agent.getDamage();
    if ( hp == 0 || damage == 0 ) {
      return false;
    }
    int activeTime = ( hp / damage ) + ( ( hp % damage ) != 0 ? 1 : 0 );
    if ( this.kernelTime == -1 ) {
      try {
        this.kernelTime = this.scenarioInfo.getKernelTimesteps();
      } catch ( NoSuchConfigOptionException e ) {
        this.kernelTime = -1;
      }
    }
    return damage >= this.thresholdRest
        || ( activeTime + this.agentInfo.getTime() ) < this.kernelTime;
  }


  private EntityID convertArea( EntityID targetID ) {
    StandardEntity entity = this.worldInfo.getEntity( targetID );
    if ( entity == null ) {
      return null;
    }
    if ( entity instanceof Human ) {
      Human human = (Human) entity;
      if ( human.isPositionDefined() ) {
        EntityID position = human.getPosition();
        if ( this.worldInfo.getEntity( position ) instanceof Area ) {
          return position;
        }
      }
    } else if ( entity instanceof Area ) {
      return targetID;
    } else if ( entity.getStandardURN() == BLOCKADE ) {
      Blockade blockade = (Blockade) entity;
      if ( blockade.isPositionDefined() ) {
        return blockade.getPosition();
      }
    }
    return null;
  }
}