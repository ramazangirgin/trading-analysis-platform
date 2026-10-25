<script setup lang="ts">
import { useSessionStore } from '@/features/auth'
import DashboardPage from './DashboardPage.vue'
import LandingPage from './LandingPage.vue'

const session = useSessionStore()
</script>

<template>
  <DashboardPage v-if="session.user" />
  <LandingPage v-else />
</template>
