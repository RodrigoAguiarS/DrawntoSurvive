package com.rodrigo.drawntosurvive

import com.rodrigo.drawntosurvive.game.*
import org.junit.Assert.*
import org.junit.Test

class GameLogicTest {
 @Test fun normalize(){val v=Vector2(3f,4f).normalized();assertEquals(.6f,v.x,.001f);assertEquals(.8f,v.y,.001f)}
 @Test fun direction8(){assertEquals(Direction8.EAST,Direction8.from(Vector2(1f,0f)));assertEquals(Direction8.NORTH_WEST,Direction8.from(Vector2(-1f,-1f)))}
 @Test fun collision(){assertTrue(GameMath.circlesCollide(Vector2(),10f,Vector2(19f,0f),10f));assertFalse(GameMath.circlesCollide(Vector2(),10f,Vector2(21f,0f),10f))}
 @Test fun sweptProjectileCollision(){assertTrue(GameMath.segmentHitsCircle(Vector2(0f,0f),Vector2(100f,0f),Vector2(50f,4f),8f));assertFalse(GameMath.segmentHitsCircle(Vector2(0f,0f),Vector2(100f,0f),Vector2(50f,20f),8f))}
 @Test fun spread(){assertEquals(listOf(-10f,0f,10f),GameMath.spreadAngles(3));assertEquals(listOf(-5f,5f),GameMath.spreadAngles(2))}
 @Test fun radial(){val d=GameMath.radialDirections(12);assertEquals(12,d.size);assertEquals(1f,d[0].x,.001f);assertEquals(1f,d[3].y,.001f)}
 @Test fun progression(){assertEquals(20,GameMath.xpRequired(1));assertEquals(44,GameMath.xpRequired(3));assertEquals(4,GameMath.coins(20,false));assertEquals(29,GameMath.coins(20,true));assertEquals(GameConfig.MIN_SPAWN_INTERVAL,GameMath.spawnInterval(600f),.001f)}
 @Test fun nearbyEnemiesSeparateWithoutNaN(){
  val engine=GameEngine(TouchController()){};engine.resize(1000,600);engine.enemies.clear()
  val a=Enemy(Vector2(300f,300f),EnemyType.SLIME,jumpTimer=2f);val b=Enemy(Vector2(302f,300f),EnemyType.SLIME,jumpTimer=3f);engine.enemies+=a;engine.enemies+=b
  val before=kotlin.math.abs(a.position.x-b.position.x);engine.update(.05f);val after=kotlin.math.abs(a.position.x-b.position.x)
  assertTrue(after>before);assertTrue(a.position.x.isFinite()&&a.position.y.isFinite());assertTrue(b.position.x.isFinite()&&b.position.y.isFinite())
 }
 @Test fun coincidentEnemiesUseSafeDeterministicSeparation(){
  val engine=GameEngine(TouchController()){};engine.resize(1000,600);engine.enemies.clear();repeat(8){engine.enemies+=Enemy(Vector2(300f,300f),EnemyType.SLIME,jumpTimer=2f+it*.1f)}
  engine.update(.05f);assertTrue(engine.enemies.all{it.position.x.isFinite()&&it.position.y.isFinite()});assertTrue(engine.enemies.map{it.position.x to it.position.y}.distinct().size>1)
 }
 @Test fun jumpStartsOnceAndReturnsToWalk(){
  val engine=GameEngine(TouchController()){};engine.resize(1000,600);engine.enemies.clear();val enemy=Enemy(Vector2(300f,300f),EnemyType.SLIME,jumpTimer=.01f);engine.enemies+=enemy
  engine.update(.02f);assertEquals(AnimationState.JUMP,enemy.animationState);engine.update(.1f);assertTrue(enemy.animationTime>.04f);repeat(18){engine.update(.1f)};assertEquals(AnimationState.WALK,enemy.animationState);assertTrue(enemy.jumpTimer>=GameConfig.MIN_JUMP_INTERVAL)
 }
 @Test fun specialPlaysJumpBeforeFiringRadialProjectiles(){
  val controls=TouchController();val engine=GameEngine(controls){};engine.resize(1000,600);engine.enemies.clear();engine.projectiles.clear();engine.player.facingDirection=Vector2(-1f,0f)
  controls.requestSpecial();engine.update(.01f)
  assertTrue(engine.specialAnimationTimer>0f);assertEquals(0,engine.projectiles.size);assertEquals(-1f,engine.specialDirection.x,.001f)
  repeat(8){engine.update(.05f)};assertEquals(0,engine.projectiles.size)
  engine.update(.05f);assertEquals(GameConfig.SPECIAL_PROJECTILE_COUNT,engine.projectiles.size)
 }
 @Test fun restartResetsRunStateWithoutReplacingEngine(){
  var results=0;val controls=TouchController();val engine=GameEngine(controls){results++};engine.resize(1000,600)
  repeat(3){runIndex->
   engine.player.currentHp=0f;engine.update(.016f);assertEquals(GameState.GAME_OVER,engine.state);assertEquals(runIndex+1,results)
   engine.enemies+=Enemy(Vector2(1f,1f),EnemyType.SLIME);engine.projectiles+=Projectile(Vector2(),Vector2(1f,0f),1f,1f,1f,1f,false);engine.requestRestart();engine.update(.016f)
   assertEquals(GameState.RUNNING,engine.state);assertEquals(GameConfig.PLAYER_INITIAL_HP,engine.player.currentHp,.001f);assertEquals(0,engine.kills);assertTrue(engine.elapsed<.1f);assertTrue(engine.projectiles.isEmpty());assertEquals(0f,engine.specialAnimationTimer,.001f)
  }
  assertEquals(3,results)
 }
}
