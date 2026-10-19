<script setup lang="ts">
import { computed } from 'vue'
import { NFormItem, NInput, NRadio, NRadioGroup, NSpace } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'

defineProps<{ messages: string[] }>()
// null: the backend generates the password; a string (also empty while typing): the one entered.
const password = defineModel<string | null>({ required: true })

const { t } = useLabels()

const mode = computed({
  get: () => (password.value === null ? 'generate' : 'enter'),
  set: (value: string) => (password.value = value === 'generate' ? null : ''),
})
</script>

<template>
  <NFormItem
    :label="t('users.password.label')"
    :validation-status="messages.length ? 'error' : undefined"
  >
    <NSpace vertical :size="8" style="width: 100%">
      <NRadioGroup v-model:value="mode">
        <NSpace>
          <NRadio value="generate">{{ t('users.password.generate') }}</NRadio>
          <NRadio value="enter">{{ t('users.password.enter') }}</NRadio>
        </NSpace>
      </NRadioGroup>
      <NInput
        v-if="password !== null"
        v-model:value="password"
        type="password"
        show-password-on="click"
        :input-props="{ autocomplete: 'new-password', name: 'one-time-password' }"
        :placeholder="t('users.password.placeholder')"
      />
    </NSpace>
    <template v-if="messages.length" #feedback>
      <div v-for="message in messages" :key="message">{{ message }}</div>
    </template>
  </NFormItem>
</template>
