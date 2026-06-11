<template>
  <TopMenuBar />
  <main class="app-main">
    <router-view />
  </main>
</template>

<script setup>
import TopMenuBar from './components/TopMenuBar.vue'
</script>

<style>
.app-main {
  min-height: calc(100vh - 28px);
  padding-top: 28px;
}
</style>
